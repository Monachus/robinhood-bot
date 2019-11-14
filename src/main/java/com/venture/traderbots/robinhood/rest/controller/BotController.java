package com.venture.traderbots.robinhood.rest.controller;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.venture.traderbots.robinhood.component.QuoteSubscriber;

import conrad.weiser.robinhood.api.RobinhoodApi;
import conrad.weiser.robinhood.api.endpoint.account.data.AccountElement;
import conrad.weiser.robinhood.api.endpoint.account.data.enums.PositionElement;
import conrad.weiser.robinhood.api.throwables.RobinhoodApiException;
import conrad.weiser.robinhood.api.throwables.RobinhoodNotLoggedInException;

@Controller
public class BotController {

	// private Object accessObject;
	@Autowired
	private RobinhoodApi api;

	@Autowired
	private Executor executor;

	@Autowired
	private QuoteSubscriber subscriber;
	Logger logger = LoggerFactory.getLogger(BotController.class);

	@RequestMapping(value = "/watchlist", produces = "application/json")
	@ResponseBody
	public ResponseEntity getWatchlist() {
		ResponseEntity result = null;
		if (subscriber.isActive())
			try {
				subscriber.destroy();
				result = subscriberStoppedResponseEntity();
			} catch (Exception e) {
				logger.error("failed to stop quote subscriber", e);
				result = subscriberStoppedResponseEntity();
			}
		else {
			result = startSubscriber();
		}
		return result;

	}

	private ResponseEntity subscriberStoppedResponseEntity() {
		ResponseEntity result;
		ObjectNode resultObject = new ObjectMapper().createObjectNode();
		resultObject.put("result", "subscriber stopped");
		ResponseEntity responseEntity = new ResponseEntity(resultObject, HttpStatus.OK);
		result = responseEntity;
		return result;
	}

	private ResponseEntity startSubscriber() {
		try {
			List<PositionElement> watchlist = api.getAccountWatchlist();
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			String json = gson.toJson(watchlist);
			logger.info(json);
			for (PositionElement instrument : watchlist) {
				if (logger.isDebugEnabled()) {
					logger.debug("stock name " + instrument.getStockName() + "is added as instrument id: "
							+ instrument.getId());
					subscriber.add(instrument);
				}
			}
			executor.execute(subscriber);

			return new ResponseEntity(watchlist, HttpStatus.OK);
		} catch (RobinhoodNotLoggedInException | RobinhoodApiException e) {
			logger.error("exception while obtaining account data", e);
			return new ResponseEntity(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
