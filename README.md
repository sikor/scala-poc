# UDP Servers Comparison

![alt tag](https://github.com/sikor/scala-poc/blob/master/doc/udpServersComparison_html_8f6433aa.png)

name|localhost |	remote clients	| [*1000 request – response per second, maximally]
----|---|----|----
blocking with one reader and one writer thread|	380|	550	|californium library uses this approach and announce almost 400k coap req per second
blocking one thread	|340	|510|	it has about 380k at the beginning and later 510k.
blocking two threads|	380|	440|	lock congestion
nio one thread with spinning|	270|	430|	(340 – 430)
nio one reader and one writer thread with spinning|	350|	405	
nio one reader and one writer thread|	320	|390	
nio one thread|	270|	380	|(300 – 380)
netty	|170	|250	
akka|	155|	190	
