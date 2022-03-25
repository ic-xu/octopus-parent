#!/bin/sh
#
# Copyright (c) 2012-2015 Andrea Selva
#
echo ""
echo "                                   ####jW KK##                                       "
echo "                                ;#GE   W#  j  ###                                     "
echo "                               #,#G # :####K#,## ##                                   "
echo "                           # . D###,.##W Di t#,#####t##                               "
echo "                          ####   ## E#####,#G ##      tf,                        "
echo "                          tj#     ######L#W,DGG#       f#i                        "
echo "                            #      ##### ## ###      :#,:                        "
echo "                           ;K#     #####:E###E#     f##f                         "
echo "                             K#Wt:L,##t#####D#EW######                            "
echo "                               #######         #######                     "
echo "                               #######   MQTT  #######                       "
echo "                               #######         #######                       "
echo "                               ###################f###                   "
echo "                               #########K#######E#####              "
echo "                               fDi   tWWW:  DD#;   ;LW                  "
echo "              .W#t             iD    :EDK#   ;#GD  .ffj               #      "
echo "              fEG              GG#   # E##.   f#D;   #,#K#           GKt     "
echo "             D#E:              KK     jGE#jf  Effi:    #Ej#D           WLK       "
echo "              #EK#           #fW#     ####j   t:. i    .jK #           tKEt:       "
echo "              .K #G        E.LDD     ,EGj;    ####Wt    # i###        j##;        "
echo "              #j#W:#      #jGLK#      ##DjK   ;t#LG      ###D# W     #GW        "
echo "               #KL##W #,#DLjf#.#       GGE,i   #GG#L     #GWWL#W:#KjW#      "
echo "                   ########           fL#DD     #GfGE      # ##.;G#             "
echo "                                     #KKK:W      iKKt                          "
echo "                                  KKEtE            G:K                          "
echo ""
echo ""
echo "                                                          "
echo ""
echo "    MQTT SERVER  version: 0.20"
echo ""

cd "$(dirname "$0")"

# resolve links - $0 may be a softlink
PRG="$0"
while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

# Get standard environment variables
PRGDIR=`dirname "$PRG"`

# Only set OCTOPUS_HOME if not already set
[ -f "$OCTOPUS_HOME"/bin/octopus.sh ] || OCTOPUS_HOME=`cd "$PRGDIR/.." ; pwd`
export OCTOPUS_HOME

# Set JavaHome if it exists
if [ -f "${JAVA_HOME}/bin/java" ]; then 
   JAVA=${JAVA_HOME}/bin/java
else
   JAVA=java
fi
export JAVA

LOG_FILE=$OCTOPUS_HOME/config/octopus-log.properties
OCTOPUS_PATH=$OCTOPUS_HOME/
#LOG_CONSOLE_LEVEL=info
#LOG_FILE_LEVEL=fine
JAVA_OPTS_SCRIPT="-XX:+HeapDumpOnOutOfMemoryError -Djava.awt.headless=true"

## Use the Hotspot garbage-first collector.
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"

## Have the JVM do less remembered set work during STW, instead
## preferring concurrent GC. Reduces p99.9 latency.
JAVA_OPTS="$JAVA_OPTS -XX:G1RSetUpdatingPauseTimePercent=5"

## Main G1GC tunable: lowering the pause target will lower throughput and vise versa.
## 200ms is the JVM default and lowest viable setting
## 1000ms increases throughput. Keep it smaller than the timeouts.
JAVA_OPTS="$JAVA_OPTS -XX:MaxGCPauseMillis=500"

## Optional G1 Settings

# Save CPU time on large (>= 16GB) heaps by delaying region scanning
# until the heap is 70% full. The default in Hotspot 8u40 is 40%.
#JAVA_OPTS="$JAVA_OPTS -XX:InitiatingHeapOccupancyPercent=70"

# For systems with > 8 cores, the default ParallelGCThreads is 5/8 the number of logical cores.
# Otherwise equal to the number of cores when 8 or less.
# Machines with > 10 cores should try setting these to <= full cores.
#JAVA_OPTS="$JAVA_OPTS -XX:ParallelGCThreads=16"

# By default, ConcGCThreads is 1/4 of ParallelGCThreads.
# Setting both to the same value can reduce STW durations.
#JAVA_OPTS="$JAVA_OPTS -XX:ConcGCThreads=16"

### GC logging options -- uncomment to enable

JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCDetails"
#JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCDateStamps"
#JAVA_OPTS="$JAVA_OPTS -XX:+PrintHeapAtGC"
#JAVA_OPTS="$JAVA_OPTS -XX:+PrintTenuringDistribution"
#JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCApplicationStoppedTime"
#JAVA_OPTS="$JAVA_OPTS -XX:+PrintPromotionFailure"
#JAVA_OPTS="$JAVA_OPTS -XX:PrintFLSStatistics=1"
#JAVA_OPTS="$JAVA_OPTS -Xloggc:/var/log/octopus/gc.log"
JAVA_OPTS="$JAVA_OPTS -Xloggc:$OCTOPUS_HOME/gc.log"
#JAVA_OPTS="$JAVA_OPTS -XX:+UseGCLogFileRotation"
#JAVA_OPTS="$JAVA_OPTS -XX:NumberOfGCLogFiles=10"
#JAVA_OPTS="$JAVA_OPTS -XX:GCLogFileSize=10M"


#开启对象指针压缩
JAVA_OPTS="$JAVA_OPTS -XX:+UseCompressedOops"
#指定堆大小
JAVA_OPTS="$JAVA_OPTS -Xms10240M -Xmx10240M"
#GC 日志
#开启打印日志
JAVA_OPTS="$JAVA_OPTS -verbose:gc"
#JAVA_OPTS="$JAVA_OPTS -Xlog:gc:$OCTOPUS_HOME/gc.log"
#JAVA_OPTS="$JAVA_OPTS -verbose:gc:$OCTOPUS_HOME/gc.log"
#JAVA_OPTS="$JAVA_OPTS -Xlog:gc*,safepoint:$OCTOPUS_HOME/gc.log:time,uptime:filecount=100,filesize=50M"
#输出gc时堆信息
JAVA_OPTS="$JAVA_OPTS -Xlog:gc+heap=trace"
JAVA_OPTS="$JAVA_OPTS -Xlog:task*=debug"

#开启java 堆优化
#JAVA_OPTS="$JAVA_OPTS -XX:+AggressiveHeap"
#除非幸存区不能容纳该对象，不然不会提升到old space
JAVA_OPTS="$JAVA_OPTS -XX:+NeverTenure"
#字符串去重
JAVA_OPTS="$JAVA_OPTS -XX:+UseStringDeduplication"
JAVA_OPTS="$JAVA_OPTS -Dio.netty.tryReflectionSetAccessible=true"
JAVA_OPTS="$JAVA_OPTS --add-exports java.base/jdk.internal.misc=ALL-UNNAMED"
echo "java home is $JAVA"
$JAVA -server $JAVA_OPTS $JAVA_OPTS_SCRIPT -Dlog4j.configuration="file:$LOG_FILE" -Doctopus.path="$OCTOPUS_PATH" -cp "$OCTOPUS_HOME/broker/target/*" io.octopus.broker.Server
