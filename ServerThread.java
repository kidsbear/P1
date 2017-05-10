import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class ServerThread implements Runnable {
	private DatagramSocket UDPsock;
	private ServerSocket TCPserver;
	private Socket TCPsock;
	private DataOutputStream TCPoutput;

	short studentID;
	int port;
	InetAddress address;

	public ServerThread(DatagramPacket packet, short studentID) {
		this.studentID = studentID;
		this.port = packet.getPort();
		this.address = packet.getAddress();
	}

	int num, len, udp_port, secretA;

	public boolean stageA() {
		this.num = 1 + Util.rand.nextInt(99);
		this.len = 1 + Util.rand.nextInt(499);
		this.udp_port = Util.getPort();
		this.secretA = Util.rand.nextInt(100);

		try {
			UDPsock = new DatagramSocket(this.udp_port);
		} catch (SocketException e) {
			System.out.println(this.studentID + " " + e.getMessage());
			return false;
		}

		ByteBuffer payload = ByteBuffer.allocate(16);
		payload.putInt(this.num);
		payload.putInt(this.len);
		payload.putInt(this.udp_port);
		payload.putInt(this.secretA);

		byte[] header = Util.getHeader(payload.arrayOffset(), 0, Util.server_step, studentID);
		byte[] message = Util.wrapMessage(header, payload.array());

		DatagramPacket packet = new DatagramPacket(message, message.length, this.address, this.port);
		try {
			UDPsock.send(packet);
		} catch (IOException e) {
			System.out.println(this.studentID + " " + e.getMessage());
			this.UDPsock.close();
			return false;
		}
		return true;
	}

	int tcp_port, secretB;

	public boolean stageB() {
		try {
			this.UDPsock.setSoTimeout(3000);

			int expectLength = 12 + ((this.len + 3) / 4 + 1) * 4;

			byte[] receive = new byte[1024];
			boolean[] checker = new boolean[this.num];
			int counter = 0;

			DatagramPacket readPacket = new DatagramPacket(receive, receive.length);

			while (counter < this.num) {
				this.UDPsock.receive(readPacket);
				byte[] data = Arrays.copyOfRange(readPacket.getData(), readPacket.getOffset(),
						readPacket.getOffset() + readPacket.getLength());
				ByteBuffer readMessage = ByteBuffer.wrap(data);

				if (data.length == expectLength && Util.verifyHeader(readMessage, this.len + 4, this.secretA,
						Util.client_step, this.studentID)) {

					int packet_id = readMessage.getInt();
					if (0 <= packet_id && packet_id < this.num
							&& verifyBody(readMessage, expectLength - 16, (byte) 0)) {
						// send message back
						byte[] header = Util.getHeader(4, this.secretA, Util.server_step, this.studentID);
						byte[] payload = ByteBuffer.allocate(4).putInt(packet_id).array();
						byte[] message = Util.wrapMessage(header, payload);

						this.port = readPacket.getPort();
						this.address = readPacket.getAddress();
						DatagramPacket sendPacket = new DatagramPacket(message, message.length, this.address,
								this.port);

						this.UDPsock.send(sendPacket);

						if (checker[packet_id] == false) {
							checker[packet_id] = true;
							counter++;
						}
					} else {
						this.UDPsock.close();
						System.out.println(this.studentID + " wrong packet_id");
						return false;
					}
				} else {
					this.UDPsock.close();
					System.out.println(this.studentID + " wrong header");
					return false;
				}
			}

			// send final message

			this.tcp_port = Util.getPort();
			this.secretB = Util.rand.nextInt(100);
			ByteBuffer payload = ByteBuffer.allocate(8);
			payload.putInt(this.tcp_port);
			payload.putInt(this.secretB);
			byte[] header = Util.getHeader(payload.arrayOffset(), this.secretA, Util.server_step, this.studentID);
			byte[] message = Util.wrapMessage(header, payload.array());

			DatagramPacket finalPacket = new DatagramPacket(message, message.length, this.address, this.port);
			this.UDPsock.send(finalPacket);
			try {
				this.TCPserver = new ServerSocket(this.tcp_port);
			} catch (IOException e) {
				System.out.println(this.studentID + " tcp failed");
				this.UDPsock.close();
				return false;
			}
			this.UDPsock.close();
			return true;
		} catch (Exception e) {
			this.UDPsock.close();
			System.out.println(this.studentID + " " + e.getMessage());
			return false;
		}
	}

	int num2, len2, secretC;
	byte c;

	public boolean stageC() {

		try {
			while (true) {
				this.TCPsock = TCPserver.accept();
				this.TCPsock.setSoTimeout(3000);
				this.num2 = 1 + Util.rand.nextInt(99);
				this.len2 = 1 + Util.rand.nextInt(499);
				this.secretC = Util.rand.nextInt(100);
				this.c = (byte)(Util.rand.nextInt(26) + 'a');


				ByteBuffer payload = ByteBuffer.allocate(13);
				payload.putInt(this.num2);
				payload.putInt(this.len2);
				payload.putInt(this.secretC);
				payload.put(c);

				byte[] header = Util.getHeader(payload.arrayOffset(), this.secretB, Util.server_step, this.studentID);
				byte[] message = Util.wrapMessage(header, payload.array());

				this.TCPoutput = new DataOutputStream(this.TCPsock.getOutputStream());
				this.TCPoutput.write(message);
				return true;
			}
		} catch (Exception e) {
			try {
				this.TCPsock.close();
				this.TCPserver.close();
			} catch (IOException e1) {
				return false;
			}
			return false;
		}

	}

	int secretD;
	
	public boolean stageD() {
		int count = 0;
		try {
			DataInputStream input = new DataInputStream(this.TCPsock.getInputStream());
			while (count < this.num2) {
				int expectLen = ((12 + this.len2 - 1) / 4 + 1) * 4;
				byte[] buffer = new byte[expectLen];
				input.read(buffer);
				ByteBuffer message = ByteBuffer.wrap(buffer);

				if (Util.verifyHeader(message, this.len2, this.secretC, Util.client_step, this.studentID)
						&& verifyBody(message, this.len2, this.c)) {
					count++;
				} else {
					input.close();
					this.TCPoutput.close();
					this.TCPsock.close();
					this.TCPserver.close();
					return false;
				}
			}

			this.secretD = Util.rand.nextInt(100);
			byte[] payload = ByteBuffer.allocate(4).putInt(this.secretD).array();
			byte[] header = Util.getHeader(payload.length, this.secretC, Util.server_step, this.studentID);
			byte[] message = Util.wrapMessage(header, payload);

			this.TCPoutput.write(message);
			input.close();
			this.TCPoutput.close();
			this.TCPsock.close();
			this.TCPserver.close();
			return true;
		} catch (Exception e) {
			try {
				this.TCPoutput.close();
				this.TCPsock.close();
				this.TCPserver.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return false;
		}

	}

	@Override
	public void run() {
		if (this.stageA()) {
			System.out.println("StageA completed for client " + this.address.getHostName() + " without error");
			if (this.stageB()) {
				System.out.println("StageB completed for client " + this.address.getHostName() + " without error");
				if (this.stageC()) {
					System.out.println("StageC completed for client " + this.address.getHostName() + " without error");
					if (this.stageD()) {
						System.out.println("StageD completed for client " + this.address.getHostName() + " without error");
					}
				}
			}
		}
	}

	private boolean verifyBody(ByteBuffer message, int len, byte c) {
		int totalLength = ((len - 1) / 4 + 1) * 4;
		int index = 0;
		while (index < len) {
			if (message.get() != c) {
				return false;
			}
			index++;
		}
		while (index < totalLength) {
			if (message.get() != 0) {
				return false;
			}
			index++;
		}
		return true;
	}
}