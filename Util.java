import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Random;

public class Util {
	static Random rand = new Random();
	static short client_step = 1;
	static short server_step = 2;

	public static byte[] getHeader(int payload_len, int psecret, short step, short studentID) {
		ByteBuffer header = ByteBuffer.allocate(12);
		header.putInt(payload_len);
		header.putInt(psecret);
		header.putShort(step);
		header.putShort(studentID);
		return header.array();
	}

	public static byte[] wrapMessage(byte[] header, byte[] payload) {
		byte[] message = new byte[((payload.length + header.length - 1) / 4 + 1) * 4];
		int idx = 0;
		for (byte b : header)
			message[idx++] = b;
		for (byte b : payload)
			message[idx++] = b;
		return message;
	}

	public static int getPort() {
		int port = 1024 + rand.nextInt(65536 - 1024);
		while (true) {
			if (isLocalPortFree(port)) {
				return port;
			} else {
				port = 1024 + rand.nextInt(65536 - 1024);
			}
		}
	}

	public static byte[] sendUDP(String host, int port, byte[] message, int rec_len, int timeOut, DatagramSocket sock)
			throws IOException {
		byte[] receive = new byte[rec_len];
		InetAddress ip = InetAddress.getByName(host);
		DatagramPacket sendPacket = new DatagramPacket(message, message.length, ip, port);
		while (true) {
			sock.send(sendPacket);
			DatagramPacket receivePacket = new DatagramPacket(receive, receive.length);
			try {
				// socket time out after 3000 ms
				sock.setSoTimeout(timeOut);
				// recieve data until from server
				while (true) {
					sock.receive(receivePacket);
					if (receivePacket.getAddress().equals(ip)) {
						return receive;
					}
				}
			} catch (SocketTimeoutException e) {
				System.out.println("ip: " + ip + " timeout. retry");
			}
		}
	}

	private static boolean isLocalPortFree(int port) {
		try {
			new ServerSocket(port).close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public static boolean verifyHeader(ByteBuffer message, int payloadLength, int psecret, short step,
			short verifiedID) {
		return message.getInt() == payloadLength && message.getInt() == psecret && message.getShort() == step
				&& verifiedID == message.getShort();
	}

	public static boolean verifyHeader(ByteBuffer message, int payloadLength, int psecret, short step) {
		return message.getInt() == payloadLength && message.getInt() == psecret && message.getShort() == step;
	}
}
