

#手机屏幕共享APP
***
##主要包括Android手机端和PC端
###手机客户端（Client）
目前界面比较简单。

![Android Client](https://github.com/zhouguangfu09/Screenshot-non-root-/blob/master/png/1.png)
***
###PC端软件（server）
1. 界面如下

   ![PC Server](https://github.com/zhouguangfu09/Screenshot-non-root-/blob/master/png/2.png)

2. 当有客户端（安卓手机）连接时，会自动弹出一个跟手机屏幕大小相等的窗口，实时显示得到屏幕截图。
****
###实时显示
目前分辨率较低的手机可以达到4-5帧，还有很大的优化空间。

1. ![PC Server](https://github.com/zhouguangfu09/Screenshot-non-root-/blob/master/png/3.png)上图是在activity界面下拉通知栏，然后右侧DDMS显示heap里面的内存分配变化，基本变化不大，还是比较稳定的（手动释放了一些内存，之前可能由于android的GC没有及时回收）。

2. ![Android Screenshot](https://github.com/zhouguangfu09/Screenshot-non-root-/blob/master/png/4.png)
上图是两个手机连接PC时手机端的屏幕共享，左图是Sony MT15i，安卓4.04的系统
右图是Galaxy S I9000,安卓4.2.2的系统。目前只有两部手机，测试的话没有大的问题，多部手机的话PC端的处理方式类似，基本无需改动代码。

###开启截屏服务
非root手机的屏幕共享问题。按下属步骤操作即可：

命令依次为(#为terminal的命令提示符)：

`
\# adb shell
\# su
\# chmod 777 /data/local
\# exit
`

###目前存在的问题
1. 可以借助PC上的VNC远程桌面的思路，每次只传送变化的数据，这样可以大大节省带宽。在屏幕内容变化不是太大的情况下可以达到局域网内的手机屏幕实时显示。

2. 利用jpeg压缩的C library， 在local service截取到framebuffer数据后快速进行压缩，这样在传输给app的client端，然后app的client不再对framebuffer数据进行bitmap封装（这个过程在java层是比较耗时的），然后立即发送给PC端的server端，这样会进一步加快screenshot的传输速度。

