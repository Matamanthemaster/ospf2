clear
#Macro to mount shared fs if it isn't already
sudo mount -a

#Macro to update jar before each exec. Update this script for next Exec.
cp -r /mnt/base/startscripts/ /mnt/base/ospf.jar ~/

sudo /usr/lib/jvm/java-17-openjdk-amd64/bin/java -Dfile.encoding=UTF-8 -jar ./ospf.jar -g -S --Standard-OSPF
#Make config file owned by the normal user if it has just been made by the jar as root.
sudo chown -R matthew:matthew ~/*
