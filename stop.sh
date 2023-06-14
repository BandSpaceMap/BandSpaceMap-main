#!/bin/bash

# 读取保存在pid.file中的进程ID，并杀掉这个进程
kill $(cat pid.file)

# 删除pid.file文件
rm pid.file

