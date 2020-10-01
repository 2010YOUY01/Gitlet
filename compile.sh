#! /bin/bash

rm -rf ~/test-gitlet
mkdir ~/test-gitlet
mkdir ~/test-gitlet/gitlet
javac -cp "/Users/yongting/Codes/skeleton-su20/library-su20/javalib/*" ./gitlet/*.java
cp ./gitlet/*.class ~/test-gitlet/gitlet
rm ./gitlet/*.class

cd "$HOME/test-gitlet"
