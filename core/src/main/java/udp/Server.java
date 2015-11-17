package udp;

/**
 * .
 */

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * 380k - 510k req p second with remote clients. (extremal jit optimalizations?)
 * 340k localhost
 */
class Server {

    private static final Statistics stats = new Statistics();


    public static void main(String args[]) throws Exception {
        InetSocketAddress bindAddr;
        if (args.length == 2) {
            bindAddr = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        } else {
            bindAddr = new InetSocketAddress(9876);
        }
        DatagramSocket serverSocket = new DatagramSocket(bindAddr);
        byte[] receiveData = new byte[1024];
        byte[] sendData = "dupa".getBytes();
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length);
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
            stats.onSent();
        }
    }
}