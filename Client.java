import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Client {
	private String host;

	private short studentID;
	private DatagramSocket UDPsock;
	private Socket TCPsock;
	private DataInputStream TCPinput;
	private DataOutputStream TCPoutput;

	public Client() {
		this((short) 262, "attu2.cs.washington.edu");
		//this((short) 262, "localhost");
	}

	public Client(short studentID, String host) {
		this.studentID = studentID;
		this.host = host;
		try {
			this.UDPsock = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	int num, len, udp_port, secretA;

	public boolean stageA() {
		System.out.println("stageA started");

		byte[] payload = "hello world\0".getBytes();
		byte[] header = Util.getHeader(payload.length, 0, Util.client_step, this.studentID);
		byte[] message = Util.wrapMessage(header, payload);

		try {
			byte[] ret = Util.sendUDP(this.host, 12235, message, 28, 3000, this.UDPsock);
			ByteBuffer answer = ByteBuffer.wrap(ret);
			// skip universal header
			answer.position(12);
			this.num = answer.getInt();
			this.len = answer.getInt();
			this.udp_port = answer.getInt();
			this.secretA = answer.getInt();

		} catch (IOException e) {
			this.UDPsock.close();
			System.out.println(e.getMessage());
			return false;
		}

		System.out.println("stageA ended, secret number from server is : " + this.secretA);
		return true;
	}

	int tcp_port, secretB;

	public boolean stageB() {
		System.out.println("stageB started");

		byte[] payload = new byte[len + 4];
		byte[] header = Util.getHeader(payload.length, this.secretA, Util.client_step, this.studentID);

		byte[] ret = null;

		ByteBuffer checker;

		for (int i = 0; i < num; i++) {
			byte[] counter = ByteBuffer.allocate(4).putInt(i).array();
			for (int j = 0; j < 4; j++)
				payload[j] = counter[j];
			byte[] message = Util.wrapMessage(header, payload);

			while (true) {
				try {
					ret = Util.sendUDP(this.host, this.udp_port, message, 16, 500, this.UDPsock);
				} catch (IOException e) {
					this.UDPsock.close();
					System.out.println(e.getMessage());
					return false;
				}
				checker = ByteBuffer.wrap(ret);
				checker.position(12);
				if (checker.getInt() == i)
					break;
			}
		}
		// receive final packet
		try {
			ret = new byte[20];
			InetAddress ip = InetAddress.getByName(host);
			DatagramPacket receivePacket = new DatagramPacket(ret, ret.length);

			this.UDPsock.setSoTimeout(3000);
			while (true) {
				this.UDPsock.receive(receivePacket);
				if (receivePacket.getAddress().equals(ip)) {
					ByteBuffer answer = ByteBuffer.wrap(ret);
					// skip universal header
					answer.position(12);
					this.tcp_port = answer.getInt();
					this.secretB = answer.getInt();

					System.out.println("stageB ended, secret number from server is : " + this.secretB);
					this.UDPsock.close();
					return true;
				}
			}
		} catch (Exception e) {
			this.UDPsock.close();
			System.out.println(e.getMessage());
			return false;
		}
	}

	int num2, len2, secretC;
	byte c;

	public boolean stageC() {
		System.out.println("stageC started");
		try {
			this.TCPsock = new Socket(this.host, this.tcp_port);
		} catch (Exception e) {
			System.out.println(e.getMessage());
			return false;
		}
		try {
			this.TCPinput = new DataInputStream(this.TCPsock.getInputStream());
			// skip header
			this.TCPinput.skip(12);

			this.num2 = this.TCPinput.readInt();
			this.len2 = this.TCPinput.readInt();
			this.secretC = this.TCPinput.readInt();
			this.c = this.TCPinput.readByte();
			// skip padding
			this.TCPinput.skip(3);

			System.out.println("stageC ended, secret number from server is : " + this.secretC);
			return true;
		} catch (IOException e) {
			try {
				this.TCPsock.close();
			} catch (IOException e1) {
				System.out.println(e1.getMessage());
			}
			System.out.println(e.getMessage());
			return false;
		}
	}

	int secretD;

	public boolean stageD() {
		System.out.println("stageD started");

		byte[] payload = new byte[this.len2];
		Arrays.fill(payload, c);
		byte[] header = Util.getHeader(payload.length, this.secretC, Util.client_step, this.studentID);
		byte[] message = Util.wrapMessage(header, payload);

		try {
			this.TCPoutput = new DataOutputStream(this.TCPsock.getOutputStream());
			for (int i = 0; i < this.num2; i++) {
				this.TCPoutput.write(message);
			}
			this.TCPinput.skip(12);
			this.secretD = this.TCPinput.readInt();
			System.out.println("stageD ended, secret number from server is : " + this.secretD);
			this.TCPsock.close();
			this.TCPinput.close();
			this.TCPoutput.close();
			return true;
		} catch (Exception e) {
			try {
				this.TCPsock.close();
				this.TCPinput.close();
			} catch (IOException e1) {
				System.out.println(e1.getMessage());
			}
			System.out.println(e.getMessage());
			return false;
		}

	}

	public static void main(String[] args) {
		Client c = new Client();
		
		if (c.stageA()) {
			System.out.println("StageA completed without error");
			if (c.stageB()) {
				System.out.println("StageB completed without error");
				if (c.stageC()) {
					System.out.println("StageC completed without error");
					if (c.stageD()) {
						System.out.println("StageD completed without error");
					}
				}
			}
		}
	}
}
