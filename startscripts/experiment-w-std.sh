#!/bin/sh
#Macro to update jar before each exec. Update this script for next Exec.
cp -r /mnt/base/startscripts/ /mnt/base/ospf.jar ~/

echo "use: ./startscripts/experiment-w-std.sh <no Adj e.g. 4> <experiment name e.g. fouradj> <node number e.g. h> <repeat id e.g. 0>"

mkdir -p ~/$2/stats/$3/ ~/$2/log/$3/

/usr/lib/jvm/java-17-openjdk-amd64/bin/java -Dfile.encoding=UTF-8 -jar ./ospf.jar -W -n $1 --Standard-OSPF -l ~/$2/log/$3/$3$4.std.$2.ospf.log -s ~/$2/stats/$3/$3$4.std.$2.ospf.stat.csv
