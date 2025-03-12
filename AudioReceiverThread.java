import CMPC3M06.AudioPlayer;
import uk.ac.uea.cmp.voip.DatagramSocket2;
import uk.ac.uea.cmp.voip.DatagramSocket3;
import uk.ac.uea.cmp.voip.DatagramSocket4;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class AudioReceiverThread implements Runnable{

    static DatagramSocket receiving_socket;

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

    public static void datagramReceived1() {
        int PORT = 55555;

        // Diffie-Hellman Parameters
        long p = 104729;                            // Larger prime number
        long g = 12345;                             // Larger base
        long receiverPrivate = 9876;                // Receiver's Private Key
        long SPV;                                    // Sender's public value
        long RPV = power(g, receiverPrivate, p);     // Receiver's public value
        long sharedKey = 0;                         // Shared secret key

        DatagramSocket receiving_socket = null;
        try {
            receiving_socket = new DatagramSocket(PORT);

            AudioPlayer player = new AudioPlayer();
            byte[] previousValidPacket = null;

            // Receive sender's public value (R1)
            byte[] buffer = new byte[512];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiving_socket.receive(packet);
            SPV = Long.parseLong(new String(packet.getData()).trim());

            // Send receiver's public value (R2) back to the sender
            InetAddress senderAddress = packet.getAddress();
            int senderPort = packet.getPort();
            String R2String = String.valueOf(RPV);
            DatagramPacket responsePacket = new DatagramPacket(R2String.getBytes(), R2String.length(), senderAddress, senderPort);
            receiving_socket.send(responsePacket);

            // Calculate shared secret key
            sharedKey = power(SPV, receiverPrivate, p);
            System.out.println("Receiver's calculated shared key: " + sharedKey);

            int receiverDebug = 1;
            while (true) {
                try {
                    buffer = new byte[512 + 4]; // + Checksum Header
                    packet = new DatagramPacket(buffer, buffer.length);
                    receiving_socket.setSoTimeout(500);
                    receiving_socket.receive(packet);

                    byte[] block = packet.getData();

                    // Generate the same pseudo-random data based on the shared key
                    byte[] randomData = new byte[block.length];
                    for (int i = 0; i < randomData.length; i++) {
                        randomData[i] = (byte) ((sharedKey * (i + 1) * 37) % 256);
                    }

                    System.out.println("Receiver Encrypted " + receiverDebug + ": " + Arrays.toString(block));

                    // Decrypt the block using the random data
                    for (int i = 0; i < block.length; i++) {
                        block[i] = (byte) (block[i] ^ randomData[i]);
                    }

                    System.out.println("Receiver Decrypted " + receiverDebug + ": " + Arrays.toString(block));
                    receiverDebug++;

                    // Extract checksum from header (first 4 bytes)
                    int receivedChecksum = ((block[0] & 0xFF) << 24) |
                            ((block[1] & 0xFF) << 16) |
                            ((block[2] & 0xFF) << 8) |
                            (block[3] & 0xFF);

                    // Calculate checksum on the decrypted audio data (excluding the header)
                    int calculatedChecksum = 0;
                    for (int i = 4; i < block.length; i++) {
                        calculatedChecksum += block[i] & 0xFF;
                    }

                    // Validate checksum and play audio
                    if (receivedChecksum == calculatedChecksum) {
                        byte[] audioData = new byte[block.length - 4];
                        System.arraycopy(block, 4, audioData, 0, audioData.length);

                        player.playBlock(audioData);

                        previousValidPacket = audioData;
                    } else {
                        if (previousValidPacket != null) {
                            System.out.println("Checksum failed. Playing previous valid packet.");
                            player.playBlock(previousValidPacket);
                        } else {
                            System.out.println("Checksum failed. No previous valid packet available.");
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error receiving packet: " + e.getMessage());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (receiving_socket != null) {
                receiving_socket.close();
            }
        }
    }

    public static void datagramReceived1NoAuth() {
        int PORT = 55555;

        // Diffie-Hellman Parameters
        long p = 104729;                            // Larger prime number
        long g = 12345;                             // Larger base
        long receiverPrivate = 9876;                // Receiver's Private Key
        long R1;                                    // Sender's public value
        long R2 = power(g, receiverPrivate, p);     // Receiver's public value
        long sharedKey = 0;                         // Shared secret key

        DatagramSocket receiving_socket = null;
        try {
            receiving_socket = new DatagramSocket(PORT);

            AudioPlayer player = new AudioPlayer();

            // Receive sender's public value (R1)
            byte[] buffer = new byte[512];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiving_socket.receive(packet);
            R1 = Long.parseLong(new String(packet.getData()).trim());

            // Send receiver's public value (R2) back to the sender
            InetAddress senderAddress = packet.getAddress();
            int senderPort = packet.getPort();
            String R2String = String.valueOf(R2);
            DatagramPacket responsePacket = new DatagramPacket(R2String.getBytes(), R2String.length(), senderAddress, senderPort);
            receiving_socket.send(responsePacket);

            // Calculate shared secret key
            sharedKey = power(R1, receiverPrivate, p);
            System.out.println("Receiver's calculated shared key: " + sharedKey);

            while (true) {
                try {
                    buffer = new byte[512];
                    packet = new DatagramPacket(buffer, buffer.length);
                    receiving_socket.setSoTimeout(500);
                    receiving_socket.receive(packet);

                    byte[] block = packet.getData();

                    // Generate the same pseudo-random mask based on the shared key
                    byte[] mask = new byte[block.length];
                    for (int i = 0; i < mask.length; i++) {
                        mask[i] = (byte) ((sharedKey * (i + 1) * 37) % 256);
                    }

                    // Decrypt the block using the mask
                    for (int i = 0; i < block.length; i++) {
                        block[i] = (byte) ((byte) (block[i] ^ mask[i]) * 1.5);
                    }

                    // Now directly play the decrypted audio data
                    byte[] audioData = block;

                    player.playBlock(audioData);

                } catch (IOException e) {
                    System.err.println("Error receiving packet: " + e.getMessage());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (receiving_socket != null) {
                receiving_socket.close();
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

    public static void datagramReceived2() {
        int PORT = 55555;

        try {
            receiving_socket = new DatagramSocket2(PORT);
        } catch (SocketException e) {
            System.out.println("ERROR: TextReceiver: Could not open UDP socket to receive from.");
            e.printStackTrace();
            System.exit(0);
        }

        boolean running = true;
        byte[][][] receivedArray = new byte[4][4][];
        AudioPlayer player;
        int currentBatch = 0;
        byte[] lastPacket = new byte[512];

        try {
            player = new AudioPlayer();
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }

        while (running) {
            try {

                // Buffer large enough for header (12 bytes) + audio data
                byte[] buffer = new byte[524]; // Increased size for the extra integer
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                receiving_socket.receive(packet);
                // Extract header information (column, row, and sequence indices)
                byte[] packetData = packet.getData();
                ByteBuffer wrapped = ByteBuffer.wrap(packetData);
                int colIndex = wrapped.getInt(0);   // Read first int (column)
                int rowIndex = wrapped.getInt(4);   // Read second int (row)
                int packetSeq = wrapped.getInt(8);  // Read third int (packet sequence)

                if (currentBatch == 0) {
                    currentBatch = packetSeq / 16;
                }

                byte[] audioData = new byte[packetData.length - 12];
                System.arraycopy(packetData, 12, audioData, 0, audioData.length);

                if (packetSeq / 16.0 <= currentBatch) {
                    receivedArray[rowIndex][colIndex] = audioData;
                } else {

                    for (int j = 0; j < 4; j++) {
                        for (int i = 3; i >= 0; i--) {
                            if (receivedArray[i][j] != null) {
                                player.playBlock(receivedArray[i][j]);
                                lastPacket = receivedArray[i][j];
                                System.out.println("Playing audio from original position [" + i + "][" + j + "]");
                            } else {
                                player.playBlock(lastPacket);
                                System.out.println("Missing packet at original position [" + i + "][" + j + "]");
                            }
                        }
                    }

                    // Increment batch counter
                    currentBatch++;

                    // Clear the array
                    receivedArray = new byte[4][4][];

                    receivedArray[rowIndex][colIndex] = audioData;
                    System.out.println("Placed first packet of new batch " + currentBatch + " at position [" + rowIndex + "][" + colIndex + "]");

                }

            } catch (IOException e) {
                System.out.println("ERROR: TextReceiver: Some IO error occurred!");
                e.printStackTrace();
            }
        }

        receiving_socket.close();
    }

    //Socket 3
    private static boolean isSilent(byte[] audioData) {
        for (byte b : audioData) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    public static void datagramReceived3() {
        int PORT = 55555;
        int bufferSize = 512;
        int jBufferSize = 10;

        try (DatagramSocket receiving_socket = new DatagramSocket(PORT)) {
            AudioPlayer player = new AudioPlayer();

            PriorityQueue<DatagramPacket> jitterBuffer = new PriorityQueue<>(
                    Comparator.comparingInt(p -> ByteBuffer.wrap(p.getData()).getInt())
            );

            Thread playbackThread = new Thread(() -> {
                int expectedPacketNumber = 0;
                byte[] previousAudio = null;

                while (!Thread.currentThread().isInterrupted()) {
                    try {

                        synchronized (jitterBuffer) {
                            while (jitterBuffer.size() < jBufferSize) {
                                jitterBuffer.wait(10);
                            }

                            // Play the next packet in order
                            DatagramPacket packet = jitterBuffer.peek();
                            if (packet == null) {
                                continue;
                            }

                            ByteBuffer packetBuffer = ByteBuffer.wrap(packet.getData());
                            int packetNumber = packetBuffer.getInt();

                            if (expectedPacketNumber == 0) {
                                expectedPacketNumber = packetNumber;
                            }

                            // If the packet is the expected one, process it
                            if (packetNumber == expectedPacketNumber) {
                                jitterBuffer.poll(); // Remove packet from buffer

                                byte[] audioData = new byte[packet.getLength() - 4];
                                packetBuffer.get(audioData);

                                // Check if the audio block is silent
                                if (isSilent(audioData)) {
                                    System.out.println("Silent audio block detected. Skipping playback.");
                                    continue;
                                }

                                System.out.println("Playing Packet Number: " + packetNumber);

                                // Play the audio block
                                player.playBlock(audioData);
                                previousAudio = audioData; // Update the last played audio
                                expectedPacketNumber++;

                            } else if (packetNumber > expectedPacketNumber) {

                                if (previousAudio != null) {
                                    System.out.println("Packet Loss Detected: Expected " + expectedPacketNumber + ", got " + packetNumber);
                                    System.out.println("Repeating last audio block to conceal loss.");
                                    player.playBlock(previousAudio);
                                    expectedPacketNumber++;
                                } else {
                                    System.out.println("Packet Loss Detected: No previous audio available, inserting silence.");
                                    player.playBlock(new byte[bufferSize - 4]);
                                    expectedPacketNumber++;
                                }
                            } else {
                                // If packet duplicate or old, remove
                                jitterBuffer.poll();
                                System.out.println("Discarding duplicate or old packet: " + packetNumber);
                            }
                        }

                    } catch (InterruptedException e) {
                        System.out.println("Playback thread interrupted: " + e.getMessage());
                        Thread.currentThread().interrupt();
                    } catch (IOException e) {
                        System.out.println("Error playing audio block: " + e.getMessage());
                    }
                }
            });
            playbackThread.start();

            while (true) {
                try {
                    byte[] buffer = new byte[bufferSize];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    receiving_socket.setSoTimeout(500);
                    receiving_socket.receive(packet);

                    ByteBuffer packetBuffer = ByteBuffer.wrap(packet.getData());
                    int packetNumber = packetBuffer.getInt();
                    byte[] audioData = new byte[packet.getLength() - 4];
                    packetBuffer.get(audioData);

                    System.out.println("Received Packet Number: " + packetNumber);

                    // Add the packet to the jitter buffer
                    synchronized (jitterBuffer) {
                        jitterBuffer.add(packet);
                        jitterBuffer.notifyAll();
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout: No packet received.");
                } catch (IOException e) {
                    System.out.println("Error receiving packet: " + e.getMessage());
                    break;
                }
            }
            playbackThread.interrupt();
            playbackThread.join();

        } catch (SocketException | LineUnavailableException | InterruptedException e) {
            System.out.println("ERROR: Initialization or cleanup failed.");
            e.printStackTrace();
        } finally {
            System.out.println("Receiver: Resources cleaned up.");
        }
    }

    //Socket 4
    public static void datagramReceived4() {
        int PORT = 55555;

        try {
            DatagramSocket4 receiving_socket = new DatagramSocket4(PORT);
            AudioPlayer player = new AudioPlayer();
            byte[] previousValidPacket = null;

            while (true) {
                byte[] buffer = new byte[512 + 4];// + checksum header
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiving_socket.receive(packet);

                // Decrypt the entire packet (+ header)
                byte[] packet2 = ByteBuffer.wrap(packet.getData()).array();

                // Extract checksum from header
                int receivedChecksum = ((packet2[0] & 0xFF) << 24) |
                        ((packet2[1] & 0xFF) << 16) |
                        ((packet2[2] & 0xFF) << 8) |
                        (packet2[3] & 0xFF);

                // Calculate checksum on decrypted audio data
                int calculatedChecksum = 0;
                for (int i = 4; i < packet2.length; i++) {
                    calculatedChecksum += packet2[i] & 0xFF;
                }

                // Validate checksum and play audio
                if (receivedChecksum == calculatedChecksum) {
                    byte[] audioData = new byte[packet2.length - 4]; // Exclude header
                    System.arraycopy(packet2, 4, audioData, 0, audioData.length);
                    player.playBlock(audioData);
                    previousValidPacket = audioData;
                } else if (previousValidPacket != null) {
                    System.out.println("Checksum failed. Playing previous valid packet.");
                    player.playBlock(previousValidPacket);
                } else {
                    System.out.println("Checksum failed. No previous valid packet available.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void textDummy(){
        int PORT = 55555;

        try{
            receiving_socket = new DatagramSocket2(PORT);
        } catch (SocketException e){
            System.out.println("ERROR: TextReceiver: Could not open UDP socket to receive from.");
            e.printStackTrace();
            System.exit(0);
        }

        boolean running = true;
        double tally = 0;
        int previousPacketNumber = 0;
        ArrayList<String> burst = new ArrayList<>();
        String burstString;

        ArrayList<String> outOfOrder = new ArrayList<>();
        String orderString;

        while (running){

            try{
                byte[] buffer = new byte[84];
                DatagramPacket packet = new DatagramPacket(buffer, 0, buffer.length);

                receiving_socket.receive(packet);

                ByteBuffer packetBuffer = ByteBuffer.wrap(packet.getData());


                int authKey = packetBuffer.getInt();

                if (authKey - previousPacketNumber > 1){
                    burstString ="Missed packets total: "+  (authKey - previousPacketNumber -1) + " Between " + previousPacketNumber + " and " + authKey;
                    burst.add(burstString);

                }

                if (authKey < previousPacketNumber) {
                    orderString = "Out-of-order packet: " + authKey + " received after " + previousPacketNumber;
                    outOfOrder.add(orderString);
                }

                previousPacketNumber = authKey;


                byte[] data = new byte[packet.getLength() - 4];
                packetBuffer.get(data);

                String str = new String(data).trim();


                if (str.equals("end")) {
                    running = false;
                } else {
                    System.out.println("Sequence Number: " + authKey + ", Data: " + str);
                    tally++;
                }



            } catch (SocketTimeoutException e){
                System.out.println("ERROR: TextReceiver: Some random IO error occured!");
                e.printStackTrace();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }

        System.out.println("""

                        TOTAL PACKETS RECEIVED

                """);

        System.out.println(tally);


        System.out.println("""

                        ALL MISSED PACKETS

                """);

        for(String s : burst){
            System.out.println(s);
        }

        System.out.println("""

                        ALL OUT OF ORDER PACKETS

                """);

        for (String s : outOfOrder) {
            System.out.println(s);
        }


        System.out.println("""

                        %PACKET LOSS

                """);


        System.out.println((((1000 - tally)/1000)*100) + "%" );
        receiving_socket.close();
    }

    public void run (){

        datagramReceived1();

    }
}
