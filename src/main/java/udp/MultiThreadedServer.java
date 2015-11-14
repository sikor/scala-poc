package udp;

/**
 * Created by Paweł Sikora.
 */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.LinkedTransferQueue;

class MultiThreadedServer {
    public static void main(String args[]) throws Exception {
        DatagramSocket serverSocket = new DatagramSocket(9876);
        serverSocket.setSendBufferSize(66000 * 100);
        serverSocket.setReceiveBufferSize(66000 * 100);
        System.out.println("receive buff size: " + serverSocket.getReceiveBufferSize());
        System.out.println("send buff size: " + serverSocket.getSendBufferSize());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                byte[] receiveData = new byte[1024];
                byte[] sendData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                String capitalizedSentence = "dupa";
                sendData = capitalizedSentence.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length);
                long testStartPeriod = System.currentTimeMillis();
                long curTime = 0;
                long handledRequests = 0;

                long allReceived = 0;
                try {
                    while (true) {
                        serverSocket.receive(receivePacket);
                        InetAddress IPAddress = receivePacket.getAddress();
                        int port = receivePacket.getPort();
                        sendPacket.setAddress(IPAddress);
                        sendPacket.setPort(port);
                        serverSocket.send(sendPacket);
                        ++handledRequests;
                        ++allReceived;
                        curTime = System.currentTimeMillis();
                        if (curTime - testStartPeriod >= 1000) {
                            System.out.println(String.valueOf(curTime - testStartPeriod) + " " + handledRequests + " all received: " + allReceived);
                            handledRequests = 0;
                            testStartPeriod = curTime;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        new Thread(runnable).start();
        Thread t2 = new Thread(runnable);
        t2.start();
        t2.join();


    }
}