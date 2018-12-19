// https://iextrading.com/developer/docs/#chart
import org.apache.log4j.{Level, Logger}
import java.io.File
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import SparkContext._
import org.apache.spark.streaming.{Seconds, StreamingContext}


import java.text.SimpleDateFormat
import org.joda.time.{DateTime, Days}
import org.apache.spark._
import org.apache.spark.sql.SQLContext._
import org.apache.spark.sql.SQLContext

import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}

// import org.json4s.jackson.JsonMethods._

import org.json4s.JsonDSL._
import scala.util.parsing.json._
import org.json4s.JsonWriter
import org.json4s.DefaultFormats
import org.json4s.ToJsonWritable
import org.apache.hadoop.yarn.webapp.ToJSON

import org.apache.spark.api.java.StorageLevels

//mongodb related imports
import com.mongodb.spark._
import org.bson.Document
import org.bson.BsonDouble
import java.util.Calendar

object ParseAndStoreStockPrices extends App {
  // Fetches per minute stock price data every day at 4:00am

  // Set logging level if log4j not configured (override by adding log4j.properties to classpath)
  Logger.getLogger("org").setLevel(Level.ERROR)
  Logger.getLogger("akka").setLevel(Level.ERROR)
  Logger.getRootLogger.setLevel(Level.ERROR)


  // Initialize a SparkConf with all available cores
  val sparkConf = new SparkConf().setAppName("StockPriceProj")

  // check Spark configuration for master URL, set it to local if not configured
  if (!sparkConf.contains("spark.master")) {
    sparkConf.setMaster("local[2]")
  }

  sparkConf.set("spark.mongodb.input.uri", "mongodb://169.53.133.179:27032/quantitative_stock_db.ticker_scrapes")
  sparkConf.set("spark.mongodb.output.uri", "mongodb://169.53.133.179:27032/quantitative_stock_db.ticker_scrapes")

  val sc = new SparkContext("local[2]", "StockPriceProj", sparkConf)
  val data = Array("AAPL","ABBV","ABT","ACN","AGN","AIG","ALL","AMGN","AMZN","AXP","BA","BAC","BIIB","BK","BKNG","BLK","BMY","BRK.B","C","CAT","CELG","CHTR","CL","CMCSA","COF","COP","COST","CSCO","CVS","CVX","DHR","DIS","DUK","DWDP","EMR","EXC","F","FB","FDX","FOX","FOXA","GD","GE","GILD","GM","GOOG","GOOGL","GS","HAL","HD","HON","IBM","INTC","JNJ","JPM","KHC","KMI","KO","LLY","LMT","LOW","MA","MCD","MDLZ","MDT","MET","MMM","MO","MRK","MS","MSFT","NEE","NFLX","NKE","NVDA","ORCL","OXY","PEP","PFE","PG","PM","PYPL","QCOM","RTN","SBUX","SLB","SO","SPG","T","TGT","TXN","UNH","UNP","UPS","USB","UTX","V","VZ","WBA","WFC","WMT","XOM")
  def get(url: String) = scala.io.Source.fromURL(url).mkString
  val documents = sc.parallelize(
    data.flatMap(name => {
      val result = get("https://api.iextrading.com/1.0/stock/" + name + "/chart/1d?format=json")
      val parsedResult = JSON.parseFull(result).get.asInstanceOf[List[Map[String,Any]]]
      parsedResult.map(minuteResult => {
        try {
          val splitTime = minuteResult("minute").asInstanceOf[String].split(":")
          val responseHour = splitTime(0)
          val responseMinute = splitTime(1)
          val uniqueId = name + responseHour + responseMinute
          val date = minuteResult("date")
          val marketChangeOverTime = minuteResult("marketChangeOverTime")
          Document.parse(s"{ticker_scrap_id: '$uniqueId', ticker_scrape_symbol: '$name', date: '$date', hour: '$responseHour', minute: '$responseMinute', marketChangeOverTime: '$marketChangeOverTime'}")
        } catch {
          case e: Exception => {
            val splitTime = minuteResult("minute").asInstanceOf[String].split(":")
            val responseHour = splitTime(0)
            val responseMinute = splitTime(1)
            val uniqueId = name + responseHour + responseMinute
            val date = minuteResult("date")
            println("Something when wrong with data for " + name + ", \nresponse: " + minuteResult + "\n, error: " + e) // squashing errors for the sake of continually streaming data
            Document.parse(s"{ticker_scrap_id: '$uniqueId', ticker_scrape_symbol: '$name', date: '$date', hour: '$responseHour', minute: '$responseMinute', marketChangeOverTime: 0}")
          }
        }
      })
    })
  )
 
  try {
    documents.saveToMongoDB()
  } catch {
    case e: Exception => println("Something when wrong with saving to mongodb\n error: " + e) // squashing error for mongo save
  }
}
