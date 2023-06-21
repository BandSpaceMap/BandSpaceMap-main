#!/bin/bash

# 运行Gradle项目，并将输出和错误重定向到output.log，然后将进程放到后台运行
nohup ./gradlew run &> output.log &

# 保存后台运行的进程ID到文件中，以便稍后停止它
echo $! > pid.file

