/*
 * TextSender.java
 */

/**
 *
 * @author  abj
 */

import CMPC3M06.AudioPlayer;
import CMPC3M06.AudioRecorder;
import uk.ac.uea.cmp.voip.DatagramSocket2;
import uk.ac.uea.cmp.voip.DatagramSocket3;
import uk.ac.uea.cmp.voip.DatagramSocket4;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class AudioSenderThread implements Runnable{

    static DatagramSocket sending_socket;

    public void start(){
        Thread thread = new Thread(this);
        thread.start();
    }

    //Socket 1
    private static long power(long base, long exponent, long mod) {
        long result = 1;
        base = base % mod;
        while (exponent > 0) {
            if ((exponent & 1) == 1) {
                result = (result * base) % mod;
            }
            exponent = exponent >> 1;
            base = (base * base) % mod;
        }
        return result;
    }

    public static void datagramSocket1() {
        int PORT = 55555;

        // Diffie-Hellman Parameters
        long p = 104729;                            // Larger prime number
        long g = 12345;                             // Larger base
        long senderPrivate = 6789;                  // Sender's private key
        long SPV = power(g, senderPrivate, p);       // Sender's public value
        long RPV;                                    // Receiver's public value
        long sharedKey = 0;                         // Shared secret key

        try {
            InetAddress clientIP = InetAddress.getByName("139.222.98.253");
            DatagramSocket sending_socket = new DatagramSocket();

            Thread.sleep(5000);
            //sending_socket.setSoTimeout(5000);

            // Send sender's public value (R1) to the receiver
            String R1String = String.valueOf(SPV);
            DatagramPacket packet = new DatagramPacket(R1String.getBytes(), R1String.length(), clientIP, PORT);
            sending_socket.send(packet);

            // Receive receiver's public value (R2)
            byte[] buffer = new byte[512];
            packet = new DatagramPacket(buffer, buffer.length);
            sending_socket.receive(packet);
            RPV = Long.parseLong(new String(packet.getData()).trim());

            // Calculate shared secret key
            sharedKey = power(RPV, senderPrivate, p);
            System.out.println("Sender's calculated shared key: " + sharedKey);

            //sending_socket.setSoTimeout(0);

            // Now proceed with audio data transmission
            AudioRecorder recorder = new AudioRecorder();

            int senderDebug = 1;

            while (true) {
                try {
                    byte[] block = recorder.getBlock();

                    // Calculate checksum for the audio data
                    int checksum = 0;
                    for (byte b : block) {
                        checksum += b & 0xFF; // Sum all bytes in the block
                    }

                    // Create packet with checksum header
                    byte[] packetData = new byte[block.length + 4]; // 4 for Checksum
                    packetData[0] = (byte) (checksum >> 24);        // First byte
                    packetData[1] = (byte) (checksum >> 16);        // Second byte
                    packetData[2] = (byte) (checksum >> 8);         // Third byte
                    packetData[3] = (byte) checksum;                // Fourth byte
                    System.arraycopy(block, 0, packetData, 4, block.length);

                    System.out.println("Sender Before Encryption " + senderDebug + ": " + Arrays.toString(packetData));

                    // Generate stronger pseudo-random data based on the shared key
                    byte[] randomData = new byte[packetData.length];
                    for (int i = 0; i < randomData.length; i++) {
                        randomData[i] = (byte) ((sharedKey * (i + 1) * 37) % 256); // Enhanced randomness
                    }

                    // Encrypt the packet using the mask
                    for (int i = 0; i < packetData.length; i++) {
                        packetData[i] = (byte) (packetData[i] ^ randomData[i]);
                    }

                    System.out.println("Sender After Encryption " + senderPrivate + ": " + Arrays.toString(packetData));
                    senderDebug++;

                    // Send the encrypted packet
                    packet = new DatagramPacket(packetData, packetData.length, clientIP, PORT);
                    sending_socket.send(packet);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (sending_socket != null) {
                sending_socket.close();
            }
        }
    }

    public static void datagramSocket1NoAuth() {
        int PORT = 55555;

        // Diffie-Hellman Parameters
        long p = 104729;                            // Larger prime number
        long g = 12345;                             // Larger base
        long senderPrivate = 6789;                  // Sender's private key
        long SPV = power(g, senderPrivate, p);       // Sender's public value
        long RPV;                                    // Receiver's public value
        long sharedKey = 0;                         // Shared secret key

        try {
            InetAddress clientIP = InetAddress.getByName("139.222.98.253");
            DatagramSocket sending_socket = new DatagramSocket();

            Thread.sleep(5000);

            // Send sender's public value (R1) to the receiver
            String R1String = String.valueOf(SPV);
            DatagramPacket packet = new DatagramPacket(R1String.getBytes(), R1String.length(), clientIP, PORT);
            sending_socket.send(packet);

            // Receive receiver's public value (R2)
            byte[] buffer = new byte[512];
            packet = new DatagramPacket(buffer, buffer.length);
            sending_socket.receive(packet);
            RPV = Long.parseLong(new String(packet.getData()).trim());

            // Calculate shared secret key
            sharedKey = power(RPV, senderPrivate, p);
            System.out.println("Sender's calculated shared key: " + sharedKey);

            // Now proceed with audio data transmission
            AudioRecorder recorder = new AudioRecorder();

            while (true) {
                try {
                    byte[] block = recorder.getBlock();

                    // Generate stronger pseudo-random data based on the shared key
                    byte[] randomData = new byte[block.length];
                    for (int i = 0; i < randomData.length; i++) {
                        randomData[i] = (byte) ((sharedKey * (i + 1) * 37) % 256); // Enhanced randomness
                    }

                    // Encrypt the packet using the mask
                    for (int i = 0; i < block.length; i++) {
                        block[i] = (byte) (block[i] ^ randomData[i]);
                    }

                    // Send the encrypted packet
                    packet = new DatagramPacket(block, block.length, clientIP, PORT);
                    sending_socket.send(packet);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (sending_socket != null) {
                sending_socket.close();
            }
        }
    }

    //Socket 2
    public static void printMatrix(byte[][][] matrix) {
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                if (matrix[i][j] != null) {
                    System.out.print(ByteBuffer.wrap(matrix[i][j]).getInt(8) + " ");
                } else {
                    System.out.print("  null ");
                }
            }
            System.out.println();
        }
    }

    public static void datagramSocket2() {
        int PORT = 55555;
        int packetSequence = 0; // Global packet counter that increments with each packet

        InetAddress clientIP = null;
        try {
            clientIP = InetAddress.getByName("localhost");
        } catch (UnknownHostException e) {
            System.out.println("ERROR: TextSender: Could not find client IP");
            e.printStackTrace();
            System.exit(0);
        }

        DatagramSocket2 sending_socket = null;
        try {
            sending_socket = new DatagramSocket2();
        } catch (SocketException e) {
            System.out.println("ERROR: TextSender: Could not open UDP socket to send from.");
            e.printStackTrace();
            System.exit(0);
        }

        AudioRecorder recorder;
        try {
            recorder = new AudioRecorder();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }

        boolean running = true;
        byte[][][] packetArray = new byte[4][4][];
        int rowIndex = 0;
        int columnIndex = 0;

        while (running) {
            try {
                byte[] block = recorder.getBlock();

                // Store the audio block for future processing
                packetArray[rowIndex][columnIndex] = block;

                columnIndex++;
                if (columnIndex == 4) {
                    columnIndex = 0;
                    rowIndex++;

                    if (rowIndex == 4) {

                        byte[][][] rotatedArray = new byte[4][4][];

                        // Create and send packets with rotated indices
                        for (int i = 0; i < 4; i++) {
                            for (int j = 0; j < 4; j++) {
                                int newRow = 3-j;
                                int newCol = i;

                                // Get the original audio data
                                byte[] audioData = packetArray[i][j];

                                // Increment sequence number for each packet
                                packetSequence++;

                                // Create packet with rotated indices and sequence number
                                ByteBuffer VoIPpacket = ByteBuffer.allocate(audioData.length + 12); // +12 for three integers
                                VoIPpacket.putInt(newCol);        // Store rotated column index
                                VoIPpacket.putInt(newRow);        // Store rotated row index
                                VoIPpacket.putInt(packetSequence); // Store unique packet sequence number
                                VoIPpacket.put(audioData);        // Add the audio data

                                byte[] packetData = VoIPpacket.array();
                                rotatedArray[newRow][newCol] = packetData;
                            }
                        }

                        printMatrix(rotatedArray);

                        for (int i = 0; i < 4; i++) {
                            for (int j = 0; j < 4; j++) {
                                byte[] rotatedBlock = rotatedArray[i][j];
                                DatagramPacket packet = new DatagramPacket(rotatedBlock, rotatedBlock.length, clientIP, PORT);
                                sending_socket.send(packet);
                            }
                        }

                        System.out.println("SENDER - All packets sent for this batch");
                        rowIndex = 0;
                    }
                }

            } catch (IOException e) {
                System.out.println("ERROR: TextSender: Some random IO error occurred!");
            }
        }

        sending_socket.close();
    }

    //Socket 3
    public static void datagramSocket3(){
        int PORT = 55555;

        InetAddress clientIP = null;
        try {
            clientIP = InetAddress.getByName("139.222.99.27");
        } catch (UnknownHostException e) {
            System.out.println("ERROR: TextSender: Could not find client IP");
            e.printStackTrace();
            System.exit(0);
        }

        try{
            sending_socket = new DatagramSocket3();
        } catch (SocketException e){
            System.out.println("ERROR: TextSender: Could not open UDP socket to send from.");
            e.printStackTrace();
            System.exit(0);
        }

        AudioRecorder recorder;
        try {
            recorder = new AudioRecorder();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }

        boolean running = true;
        int packetNumber = 1;

        while (running){
            try{
                byte[] block = recorder.getBlock();


                ByteBuffer packetBuffer = ByteBuffer.allocate(block.length + 4);
                packetBuffer.putInt(packetNumber);
                packetBuffer.put(block);

                //Creates a DatagramPacket with packet number + audio data
                DatagramPacket packet = new DatagramPacket(packetBuffer.array(), packetBuffer.array().length, clientIP, PORT);
                sending_socket.send(packet);

                System.out.println("Sent Packet Number: " + packetNumber);

                packetNumber++;

            } catch (IOException e){
                System.out.println("ERROR: TextSender: Some random IO error occurred!");
            }
        }

        sending_socket.close();
    }

    //Socket 4
    public static void datagramSocket4() {
        int PORT = 55555;

        try {
            InetAddress clientIP = InetAddress.getByName("139.222.98.115");
            DatagramSocket4 sending_socket = new DatagramSocket4();
            AudioRecorder recorder = new AudioRecorder();

            while (true) {
                byte[] block = recorder.getBlock();

                // Calculate checksum on unencrypted audio data
                int checksum = 0;
                for (byte b : block) checksum += b & 0xFF;

                // Create packet with checksum header
                byte[] packetData = new byte[block.length + 4]; // 4 bytes for checksum header
                packetData[0] = (byte) (checksum >> 24);       // First byte of checksum
                packetData[1] = (byte) (checksum >> 16);       // Second byte of checksum
                packetData[2] = (byte) (checksum >> 8);        // Third byte of checksum
                packetData[3] = (byte) checksum;               // Fourth byte of checksum
                System.arraycopy(block, 0, packetData, 4, block.length); // Add audio data

                // Send encrypted packet
                DatagramPacket packet = new DatagramPacket(packetData, packetData.length, clientIP, PORT);
                sending_socket.send(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run () {

        datagramSocket3();

    }
}