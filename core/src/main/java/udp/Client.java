package udp;

/**
 * Created by Paweł Sikora.
 */

import java.io.*;
import java.net.*;

class Client {

    private static volatile InetSocketAddress targetAddr;

    public static void main(String args[]) throws Exception {
        if (args.length >= 2) {
            targetAddr = new InetSocketAddress(InetAddress.getByName(args[0]), Integer.valueOf(args[1]));
        } else {
            targetAddr = new InetSocketAddress(InetAddress.getByName("localhost"), 9876);
        }
        System.out.println("send Address: " + targetAddr);
        Runnable client = () -> {
            try {
                DatagramSocket clientSocket = new DatagramSocket();
                byte[] sendData = new byte[1024];
                byte[] receiveData = new byte[1024];
                int counter = 0;
                String sentence = "dupa";
                sendData = sentence.getBytes();

                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, targetAddr.getAddress(), targetAddr.getPort());
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                long start = System.currentTimeMillis();
                float time = 100;

                clientSocket.setSoTimeout(10000);
                while ((System.currentTimeMillis() - start) / 1000 < time) {
                    ++counter;
                    send(clientSocket, sendPacket);
                    receive(clientSocket, receivePacket);
                }

                clientSocket.close();
                System.out.println(counter / time);
            } catch (Exception e) {
                e.printStackTrace();
            }

        };
        int tCount;
        if (args.length == 3) {
            tCount = Integer.valueOf(args[2]);
        } else {
            tCount = 1;
        }
        System.out.println("Threads count: " + tCount);
        Thread[] ts = new Thread[tCount];
        for (int i = 0; i < tCount; ++i) {
            ts[i] = new Thread(client);
            ts[i].start();
        }
        for (int i = 0; i < tCount; ++i) {
            ts[i].join();
        }
    }

    private static void receive(DatagramSocket clientSocket, DatagramPacket receivePacket) throws IOException {
        clientSocket.receive(receivePacket);
    }

    private static void send(DatagramSocket clientSocket, DatagramPacket sendPacket) throws IOException {
        clientSocket.send(sendPacket);
    }
}