import pymongo
import pandas as pd
import numpy as np
import datetime
import string
global sentiments, emotions

#Ticker
myclient = pymongo.MongoClient("mongodb://169.53.133.179:27032/")
ticker = myclient["quantitative_stock_db"]
myticker = ticker["ticker_scrapes"]

tickerDocs = myticker.find()
tickerDF = pd.DataFrame(list(tickerDocs))
tickerDF['datetimekey'] = tickerDF.date.map(str)+tickerDF.hour.map(str)+tickerDF.minute.map(str)

def parseDatetimeKey(keyStr):
        return datetime.datetime(int(keyStr[0:4]),int(keyStr[4:6]),int(keyStr[6:8]),int(keyStr[8:10]),int(keyStr[10:12]))

tickerDF['ParsedDT'] = tickerDF.datetimekey.apply(parseDatetimeKey)

#round to some chosen time
roundMinutes = 30
tickerDF['rounded_time'] = tickerDF['ParsedDT'].apply(lambda dt: datetime.datetime(dt.year, dt.month, dt.day,
        dt.hour, roundMinutes*(dt.minute // roundMinutes)))

tickerDF.marketChangeOverTime = tickerDF.marketChangeOverTime.astype(float)
tickerDFByTime = tickerDF.groupby(['ticker_scrape_symbol','rounded_time'])['marketChangeOverTime'].sum()

tickerDFByTime = pd.DataFrame(tickerDFByTime).reset_index()
symbols = tickerDFByTime.ticker_scrape_symbol.unique()
times = tickerDFByTime.rounded_time.unique()
ReshapeDF = pd.DataFrame(columns=[symbols],index=times)

#factor data into DF - columns are stocks, rows are time granularity
for symbol in symbols:
        ReshapeDF[symbol] = np.asarray(tickerDFByTime[tickerDFByTime.ticker_scrape_symbol==symbol].marketChangeOverTime)


ReshapeDF.reset_index(inplace=True)
ReshapeDF = ReshapeDF.rename({'index':'rounded_time'})

#Process data into training fromat
x_train = np.asarray([])
y_train = []
LookBack = int(360 / roundMinutes)
x_train = []
toPredict='FB'
for i in range(LookBack, len(ReshapeDF)):
        x_train.append(np.asarray(ReshapeDF.iloc[i-LookBack:i-1][symbols]))
        y_train.append(ReshapeDF[toPredict].iloc[i])

x_train = np.asarray(x_train)
y_train = np.asarray(y_train).flatten()

timesteps = np.asarray(x_train).shape[1]
Features = np.asarray(x_train).shape[2]

train_perc = 0.9
holdout = int(np.round(x_train.shape[0] * train_perc))

x_test = x_train[holdout:x_train.shape[0],:,:]
y_test = y_train[holdout:y_train.shape[0]]

x_train = x_train[0:holdout,:,:]
y_train = y_train[0:holdout]
#Train RNN:
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Dense, LSTM
RNN = Sequential()
RNN.add(LSTM(32,input_shape=(timesteps,Features)))
RNN.add(Dense(1,activation='tanh'))
RNN.compile(loss='mse',optimizer='rmsprop',metrics=['mse'])
RNN.fit(x_train, y_train, batch_size = 15, epochs=50)
import math
print("average stock price change size: ")
print(np.mean(np.abs(y_test)))
print("average error of model prediction: ")
print(np.mean(np.abs(y_test-RNN.predict(x_test))))

results = pd.DataFrame({"actual":y_test.flatten(),"predictions":RNN.predict(x_test).flatten()})

#Sentiments
myclient = pymongo.MongoClient("mongodb://169.53.133.179:27032/")
mydb = myclient["qualitative_stock_db"]
mycol = mydb["tweets"]

#load data
mydoc = mycol.find()
df = pd.DataFrame(list(mydoc))

#bucket sentiment into int range
df.Sentiment = df.Sentiment.astype(float)

df.HashTags = df.HashTags.str.lower()
TruncDF = df[(df.HashTags.str.contains("amzn")) | (df.HashTags.str.contains("aapl")) | (df.HashTags.str.contains("fb"))]
AppleDF = df[(df.HashTags.str.contains("apple")) | (df.HashTags.str.contains("aapl"))]
AmznDF = df[(df.HashTags.str.contains("amzn")) | (df.HashTags.str.contains("amazon"))]
FB_DF = df[(df.HashTags.str.contains("facebook")) | (df.HashTags.str.contains("fb"))]

AppleDF.StatusCreatedAt = pd.to_datetime(AppleDF.StatusCreatedAt)
AppleDF['rounded_create_time'] = AppleDF['StatusCreatedAt'].apply(lambda dt: datetime.datetime(dt.year, dt.month, dt.day, dt.hour,30*(dt.minute // 30)))
round_apple = AppleDF.groupby(['rounded_create_time'])['Sentiment'].mean()

AmznDF.StatusCreatedAt = pd.to_datetime(AmznDF.StatusCreatedAt)
AmznDF['rounded_create_time'] = AmznDF['StatusCreatedAt'].apply(lambda dt: datetime.datetime(dt.year, dt.month, dt.day, dt.hour,30*(dt.minute // 30)))
round_amzn = AmznDF.groupby(['rounded_create_time'])['Sentiment'].mean()

FB_DF.StatusCreatedAt = pd.to_datetime(FB_DF.StatusCreatedAt)
FB_DF['rounded_create_time'] = FB_DF['StatusCreatedAt'].apply(lambda dt: datetime.datetime(dt.year, dt.month, dt.day, dt.hour,30*(dt.minute // 30)))
round_fb = FB_DF.groupby(['rounded_create_time'])['Sentiment'].mean()

AppleDF.StatusCreatedAt = pd.to_datetime(AppleDF.StatusCreatedAt)
AppleDF['rounded_create_time'] = AppleDF['StatusCreatedAt'].apply(lambda dt: datetime.datetime(dt.year, dt.month, dt.day, dt.hour,30*(dt.minute // 30)))
round_apple = AppleDF.groupby(['rounded_create_time'])['Sentiment'].mean()

AmznDF.StatusCreatedAt = pd.to_datetime(AmznDF.StatusCreatedAt)
AmznDF['rounded_create_time'] = AmznDF['StatusCreatedAt'].apply(lambda dt: datetime.datetime(dt.year, dt.month, dt.day, dt.hour,30*(dt.minute // 30)))
round_amzn = AmznDF.groupby(['rounded_create_time'])['Sentiment'].mean()

FB_DF.StatusCreatedAt = pd.to_datetime(FB_DF.StatusCreatedAt)
FB_DF['rounded_create_time'] = FB_DF['StatusCreatedAt'].apply(lambda dt: datetime.datetime(dt.year, dt.month, dt.day, dt.hour,30*(dt.minute // 30)))
round_fb = FB_DF.groupby(['rounded_create_time'])['Sentiment'].mean()

#apple is missing some time periods of sentiment - fill them in with mean sentiment for apple
for val in [x for x in round_fb.index if x not in round_apple.index]:
		round_apple.loc[val] = np.mean(round_apple)

#join sentiment to historical stock data
Merge = pd.concat([round_apple,round_amzn,round_fb,ReshapeDF],axis=1)
ColNames = []
ColNames.append("Apple_Sentiment")
ColNames.append("Amzn_Sentiment")
ColNames.append("FB_Sentiment")
Merge.columns = ColNames + list(symbols)

#impute missing values for sentiment for each company of interest
for val in ['Apple_Sentiment','Amzn_Sentiment','FB_Sentiment']:
    impute = np.mean(Merge[val])
    Merge[val] = Merge[val].fillna(impute)

#only use data where we have a valid output
Merge_short = Merge[~pd.isna(Merge.WFC)]

#prepare data for RNN
x_train = np.asarray([])
y_train = []
LookBack = 12
x_train = []
toPredict='FB'
for i in range(LookBack, len(Merge_short)):
    x_train.append(np.asarray(Merge_short.iloc[i-LookBack:i-1][ColNames + list(symbols)]))
    y_train.append(Merge_short[toPredict].iloc[i])
	
x_train = np.asarray(x_train)
y_train = np.asarray(y_train).flatten()

timesteps = np.asarray(x_train).shape[1]
Features = np.asarray(x_train).shape[2]

train_perc = 0.9
holdout = int(np.round(x_train.shape[0] * train_perc))

x_test = x_train[holdout:x_train.shape[0],:,:]
y_test = y_train[holdout:y_train.shape[0]]

x_train = x_train[0:holdout,:,:]
y_train = y_train[0:holdout]
#Train RNN - uses sentiment
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Dense, LSTM
RNN = Sequential()
RNN.add(LSTM(32,input_shape=(timesteps,Features)))
RNN.add(Dense(1,activation='tanh'))
RNN.compile(loss='mse',optimizer='rmsprop',metrics=['mse'])
RNN.fit(x_train, y_train, batch_size = 15, epochs=50)
import math
print("average stock price change size: ")
print(np.mean(np.abs(y_test)))
print("average error of model prediction: ")
print(np.mean(np.abs(y_test-RNN.predict(x_test))))

bins = [0,1,2,3,4,5]
df['binned_sentiment'] = pd.cut(df['Sentiment'], bins)
histogramCounts = df.groupby('binned_sentiment')['Sentiment'].count()
print("Count by Sentiment bucket:")
print(histogramCounts)

#generate time series of sentiments
df.StatusCreatedAt = pd.to_datetime(df.StatusCreatedAt)
df['rounded_create_time'] = df['StatusCreatedAt'].apply(lambda dt: datetime.datetime(dt.year, dt.month, dt.day, dt.hour,30*(dt.minute // 30)))

#aggregate sentiment by time and hashtag
#very memory expensive
SentimentByTag = pd.DataFrame(df.HashTags.str.split(' ').tolist(), index=[df.Sentiment, df.rounded_create_time]).stack()

SentimentByTagDF = SentimentByTag.reset_index()
SentimentByTagDF.columns = ['Sentiment', 'Time', 'unknown', 'HashTag']
SentimentByTagDF.drop('unknown',axis=1,inplace=True)
SentimentByTagDF = SentimentByTagDF[SentimentByTagDF.HashTag != ""]
SentimentByTagAndTime = SentimentByTagDF.groupby(['HashTag','Time'])['Sentiment'].agg(['mean','count'])
SentimentByTagAndTime.reset_index(inplace=True)
SentimentByTagAndTime = SentimentByTagAndTime[SentimentByTagAndTime.HashTag != "#"]

SentimentByTagAndTime.set_index('Time',inplace=True)