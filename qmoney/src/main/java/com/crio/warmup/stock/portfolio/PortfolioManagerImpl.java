
package com.crio.warmup.stock.portfolio;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import com.crio.warmup.stock.dto.AnnualizedReturn;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.dto.PortfolioTrade;
import com.crio.warmup.stock.dto.TiingoCandle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.web.client.RestTemplate;

public class PortfolioManagerImpl implements PortfolioManager {




  private RestTemplate restTemplate;


  // Caution: Do not delete or modify the constructor, or else your build will break!
  // This is absolutely necessary for backward compatibility
  public PortfolioManagerImpl(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }








  private Comparator<AnnualizedReturn> getComparator() {
    return Comparator.comparing(AnnualizedReturn::getAnnualizedReturn).reversed();
  }

  //CHECKSTYLE:OFF

  // TODO: CRIO_TASK_MODULE_REFACTOR
  //  Extract the logic to call Tiingo third-party APIs to a separate function.
  //  Remember to fill out the buildUri function and use that.


  public List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to)
      throws JsonProcessingException {
    String uri = buildUri(symbol, from, to);
    
    try {
      TiingoCandle[] candles = restTemplate.getForObject(uri, TiingoCandle[].class);
      return Arrays.asList(candles);
    } catch (Exception e) {
      throw new RuntimeException("Error while fetching stock quotes", e);
    }
}

public static String prepareUrl(PortfolioTrade trade, LocalDate endDate, String token) {
  return "https://api.tiingo.com/tiingo/daily/" + trade.getSymbol() + "/prices?startDate=" + trade.getPurchaseDate() + "&endDate=" + endDate + "&token=" + token;
}


protected String buildUri(String symbol, LocalDate startDate, LocalDate endDate) {
  String uriTemplate = "https://api.tiingo.com/tiingo/daily/" + symbol
      + "/prices?startDate=" + startDate + "&endDate=" + endDate
      + "&token=25ccb0fd8cf05f2818b61a948b603acb33eb5d13";
  return uriTemplate;
}



@Override
public List<AnnualizedReturn> calculateAnnualizedReturn(List<PortfolioTrade> portfolioTrades,
    LocalDate endDate) {
  List<AnnualizedReturn> annualizedReturns = new ArrayList<>();
  
  for (PortfolioTrade trade : portfolioTrades) {
    try {
      List<Candle> candles = getStockQuote(trade.getSymbol(), trade.getPurchaseDate(), endDate);
      Double buyPrice = getOpeningPriceOnStartDate(candles);
      Double sellPrice = getClosingPriceOnEndDate(candles);
      
      AnnualizedReturn annualizedReturn = calculateAnnualizedReturns(endDate, trade, buyPrice, sellPrice);
      annualizedReturns.add(annualizedReturn);
      
    } catch (Exception e) {
      throw new RuntimeException("Error calculating annualized returns for symbol: " + trade.getSymbol(), e);
    }
  }

  return annualizedReturns.stream()
      .sorted(getComparator())
      .collect(Collectors.toList());
}


public static AnnualizedReturn calculateAnnualizedReturns(LocalDate endDate, PortfolioTrade trade,
    Double buyPrice, Double sellPrice) {
    double totalReturns = (sellPrice - buyPrice) / buyPrice;
    LocalDate purchaseDate = trade.getPurchaseDate();
    double totalYears = (double) ChronoUnit.DAYS.between(purchaseDate, endDate) / 365.24;
    double annualizedReturn = Math.pow(1 + totalReturns, 1 / totalYears) - 1;
    return new AnnualizedReturn(trade.getSymbol(), annualizedReturn, totalReturns);
}


public static Double getClosingPriceOnEndDate(List<Candle> candles) {
  return candles.get(candles.size() - 1).getClose();
}


public static File resolveFileFromResources(String file) throws URISyntaxException {
  return Paths.get(
      Thread.currentThread().getContextClassLoader().getResource(file).toURI()).toFile();
}




public static ObjectMapper getObjectMapper() {
  ObjectMapper objectMapper = new ObjectMapper();
  objectMapper.registerModule(new JavaTimeModule());
  objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  return objectMapper;
}

public static List<PortfolioTrade> readTradesFromJson(String filename) {
  ObjectMapper objectMapper = getObjectMapper(); // Use the provided ObjectMapper
  List<PortfolioTrade> trades;

  // Use InputStream to read from resources
  try (InputStream inputStream = PortfolioManagerImpl.class.getClassLoader().getResourceAsStream(filename)) {
      if (inputStream == null) {
          throw new IOException("File not found: " + filename);
      }
      // Read the JSON file and map it to a list of PortfolioTrade
      trades = objectMapper.readValue(inputStream, new TypeReference<List<PortfolioTrade>>() {});
      return trades;
  } catch (IOException e) {
      e.printStackTrace(); // Print stack trace for debugging
      return null; // or return an empty list instead
  }
}


public static Double getOpeningPriceOnStartDate(List<Candle> candles) {
  return candles.get(0).getOpen();
}

public static List<String> mainReadFile(String[] args) {
  if (args.length < 1) {
      return Collections.singletonList("No file provided");
  }
  
  String filename = args[0];
  try {
      File file = resolveFileFromResources(filename);
      PortfolioTrade[] trades = getObjectMapper().readValue(file, PortfolioTrade[].class);
      
      return Arrays.stream(trades)
                   .map(PortfolioTrade::getSymbol)
                   .collect(Collectors.toList());
  } catch (IOException | URISyntaxException e) {
      return Collections.singletonList("Error reading the file");
  }
}

public static List<String> mainReadQuotes(String[] args) {
  if (args.length < 2) {
      return Collections.singletonList("Insufficient arguments. Provide file name and end date.");
  }
  
  String filename = args[0];
  LocalDate endDate = LocalDate.parse(args[1]);
  
  try {
      File file = resolveFileFromResources(filename);
      PortfolioTrade[] trades = getObjectMapper().readValue(file, PortfolioTrade[].class);
      String token = "25ccb0fd8cf05f2818b61a948b603acb33eb5d13"; 
      
      // Create a list of pairs containing symbol and closing price
      List<StockWithPrice> stocksWithPrices = new ArrayList<>();
      
      for (PortfolioTrade trade : trades) {
        if (endDate.isBefore(trade.getPurchaseDate())) {
          throw new RuntimeException("End date " + endDate + " is earlier than purchase date for stock: " + trade.getSymbol());
        }
        List<Candle> candles = fetchCandles(trade, endDate, token);
        if (!candles.isEmpty()) {
            Double closingPrice = getClosingPriceOnEndDate(candles);
            stocksWithPrices.add(new StockWithPrice(trade.getSymbol(), closingPrice));
        }
      }
      
      // Sort stocks based on closing price in descending order
      stocksWithPrices.sort((a, b) -> a.getPrice().compareTo(b.getPrice()));
      
      // Return the sorted symbols
      return stocksWithPrices.stream()
                             .map(StockWithPrice::getSymbol)
                             .collect(Collectors.toList());
  } catch (IOException | URISyntaxException e) {
      return Collections.singletonList("Error reading the file");
  }
}

// Helper class to store stock symbol and its closing price
static class StockWithPrice {
  private String symbol;
  private Double price;
  
  public StockWithPrice(String symbol, Double price) {
      this.symbol = symbol;
      this.price = price;
  }
  
  public String getSymbol() {
      return symbol;
  }
  
  public Double getPrice() {
      return price;
  }
}


public static List<AnnualizedReturn> mainCalculateSingleReturn(String[] args) {
  if (args.length < 2) {
      return Collections.emptyList();
  }
  
  String filename = args[0];
  LocalDate endDate = LocalDate.parse(args[1]);
  
  try {
      File file = resolveFileFromResources(filename);
      PortfolioTrade[] trades = getObjectMapper().readValue(file, PortfolioTrade[].class);
      String token = "25ccb0fd8cf05f2818b61a948b603acb33eb5d13"; // Replace with your actual API token
      
      List<AnnualizedReturn> annualizedReturns = new ArrayList<>();
      for (PortfolioTrade trade : trades) {
          List<Candle> candles = fetchCandles(trade, endDate, token);
          if (!candles.isEmpty()) {
              Double buyPrice = getOpeningPriceOnStartDate(candles);
              Double sellPrice = getClosingPriceOnEndDate(candles);
              
              annualizedReturns.add(calculateAnnualizedReturns(endDate, trade, buyPrice, sellPrice));
          }
      }
      
      annualizedReturns.sort(Comparator.comparing(AnnualizedReturn::getAnnualizedReturn).reversed());
      return annualizedReturns;
  } catch (IOException | URISyntaxException e) {
      return Collections.emptyList();
  }
}


public static List<String> debugOutputs() {
  String valueOfArgument0 = "trades.json"; // example values
  String resultOfResolveFilePathArgs0 = "trades.json";
  String toStringOfObjectMapper = "ObjectMapper";
  String functionNameFromTestFileInStackTrace = "mainReadFile";
  String lineNumberFromTestFileInStackTrace = "10";
  
  return Arrays.asList(valueOfArgument0, resultOfResolveFilePathArgs0, toStringOfObjectMapper, functionNameFromTestFileInStackTrace, lineNumberFromTestFileInStackTrace);
}

public static List<Candle> fetchCandles(PortfolioTrade trade, LocalDate endDate, String token) {
  String url = prepareUrl(trade, endDate, token);
  RestTemplate restTemplate = new RestTemplate();
  TiingoCandle[] candles = restTemplate.getForObject(url, TiingoCandle[].class);
  
  return candles != null ? Arrays.asList(candles) : new ArrayList<>();
}

public static String getToken(){
  return "25ccb0fd8cf05f2818b61a948b603acb33eb5d13";
}



}
