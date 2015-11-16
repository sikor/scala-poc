package udp;

/**
 * Created by Paweł Sikora.
 */

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * 380k - 500k req p second
 */
class Server {
    public static void main(String args[]) throws Exception {
        InetSocketAddress bindAddr;
        if (args.length == 2) {
            bindAddr = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        } else {
            bindAddr = new InetSocketAddress(9876);
        }
        DatagramSocket serverSocket = new DatagramSocket(bindAddr);
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
        serverSocket.setSendBufferSize(66000 * 100);
        serverSocket.setReceiveBufferSize(66000 * 100);
        System.out.println("receive buff size: " + serverSocket.getReceiveBufferSize());
        System.out.println("send buff size: " + serverSocket.getSendBufferSize());
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
    }
}