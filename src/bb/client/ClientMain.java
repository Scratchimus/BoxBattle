package bb.client;

/**
 * Manages general client-side things, calling ticking/drawing, etc
 */
public class ClientMain {
	
	private static ClientMain client;
	
	private ClientMain() {
		client = this;
		System.out.println("Client");
	}
	
	public static void main(String[] arguments) {
		new ClientMain();
	}
	
	public static ClientMain getClientInstance() {
		return client;
	}
	
}
