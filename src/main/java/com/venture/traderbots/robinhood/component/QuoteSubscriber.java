package com.venture.traderbots.robinhood.component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import conrad.weiser.robinhood.api.RobinhoodApi;
import conrad.weiser.robinhood.api.endpoint.account.data.enums.PositionElement;
import conrad.weiser.robinhood.api.endpoint.fundamentals.data.TickerFundamentalElement;
import conrad.weiser.robinhood.api.endpoint.historical.data.HistoricalListElement;
import conrad.weiser.robinhood.api.endpoint.quote.data.TickerQuoteElement;
import conrad.weiser.robinhood.api.endpoint.quote.data.TickerQuoteResults;
import conrad.weiser.robinhood.api.throwables.RobinhoodApiException;
import conrad.weiser.robinhood.api.throwables.RobinhoodNotLoggedInException;
import conrad.weiser.robinhood.api.throwables.TickerNotExistsException;

@Component
public class QuoteSubscriber implements DisposableBean, Runnable {

	@Autowired
	private RobinhoodApi api;
	private Logger logger = LoggerFactory.getLogger(QuoteSubscriber.class);

	private Thread thread;
	private Set<PositionElement> instruments = new HashSet<PositionElement>();
	private HashMap<PositionElement, HistoricalListElement> instrumentHistoricalDataMap = new HashMap<PositionElement, HistoricalListElement>();
	private HashMap<String, TickerFundamentalElement> tickerFundamentalMap = new HashMap<String, TickerFundamentalElement>();
	
	public boolean add(PositionElement instrument) {
		return instruments.add(instrument);
	}

	public QuoteSubscriber() {
		this.thread = new Thread(this);
		this.thread.start();
	}

	public boolean isActive() {
		return !instruments.isEmpty();
	}

	@Override
	public void run() {
		if (api != null) {
			try {
				List<PositionElement> currentPositions = api.getAccountPositions();
				for (PositionElement currentPosition : currentPositions) {
					instruments.remove(currentPosition);
					instruments.add(currentPosition);
					tickerFundamentalMap.put(currentPosition.getStockTicker(), api.getTickerFundamental(currentPosition.getId(false)));
				}
				logger.debug("current positions to be processed by the quote subscriber:\n" + instruments);
			} catch (RobinhoodApiException | RobinhoodNotLoggedInException | TickerNotExistsException e) {
				logger.error("failed to obtain current positions", e);
			}
		}

		while (!instruments.isEmpty()) {
			logger.debug("subscriber has instruments to process?" + !instruments.isEmpty());
			synchronized (instruments) {
				for (PositionElement instrument : instruments) {

					try {
						TickerQuoteResults quotes = api.getQuoteByTicker(instrument.getId());
						TickerQuoteElement quote = quotes.getResults().get(0);

						if (!instrumentHistoricalDataMap.containsKey(instrument)) {
							String span = "3month";
							String interval = "hour";
							HistoricalListElement historicalData = api.getHistoricalData(instrument.getSymbol(),
									interval, span);
							instrumentHistoricalDataMap.put(instrument, historicalData);
							logger.debug("historical data has been stored :\n" + historicalData);
						}

						logger.debug("ask price for " + quote.getSymbol() + " is " + quote.getAsk_price());
						if (instrument.getQuantity() > 0.0) {
							logger.debug(String.format("you currently hold %s for %s  ", 
									instrument.getQuantity(),
									instrument.getStockTicker()));
							
							float totalEarnings = (quote.getBid_price() - instrument.getAverage_buy_price()) * instrument.getQuantity();
							float pe_ratio = tickerFundamentalMap.get(quote.getSymbol()).getPe_ratio();
							float dividend_yield = tickerFundamentalMap.get(quote.getSymbol()).getDividend_yield();
							
							if (instrument.getAverage_buy_price() < quote.getBid_price()) {
								logger.info(String.format("if you sell %s now you can win %s. it has PE ratio of %s and a yield of %s", 
										quote.getSymbol(),
										totalEarnings,
										pe_ratio,
										dividend_yield));
							} else {
	
								logger.info(String.format("if you sell %s now you can lose %s it has PE ratio of %s and a yield of %s", 
										quote.getSymbol(),
										totalEarnings,
										pe_ratio,
										dividend_yield));
							}
						}

					} catch (Throwable e) {
						logger.error("failed to obtain quote", e);
					}
				}
			}
		}
	}

	@Override
	public void destroy() throws Exception {
		synchronized (instruments) {
			instruments.clear();
		}
	}

}
