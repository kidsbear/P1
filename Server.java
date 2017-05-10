import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Server {

	private static final String MESSAGE = "hello world\0";
	private int port;

	public Server () {
		this(12235);
	}
	public Server(int port) {
		this.port = port;
	}


	private void runServer() {
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket(this.port);
		} catch (SocketException e) {
			e.printStackTrace();
		}

		while (true) {
			byte[] readBuffer = new byte[1024];
			DatagramPacket readPacket = new DatagramPacket(readBuffer, readBuffer.length);
			try {
				socket.receive(readPacket);
			} catch (IOException e) {
				socket.close();
				e.printStackTrace();
			}

			byte[] readData = Arrays.copyOfRange(readPacket.getData(), readPacket.getOffset(),
					readPacket.getOffset() + readPacket.getLength());

			ByteBuffer readMessage = ByteBuffer.wrap(readData);

			if (Util.verifyHeader(readMessage, MESSAGE.length(), 0, (short) 1)) {
				short studentID = readMessage.getShort();

				if (verifyBodyA(readMessage)) {
					System.out.println("Client: " + readPacket.getAddress().getHostName() + " connected.");
					new Thread(new ServerThread(readPacket, studentID)).start();
				}
			}
		}

	}
	private static boolean verifyBodyA(ByteBuffer message) {
		return (new String(message.array(), message.position(), message.remaining())).equals(MESSAGE);
	}


	public static void main(String[] args) {
		Server localServer = new Server(12235);
		System.out.println("Local server started");
		localServer.runServer();
	}

}
