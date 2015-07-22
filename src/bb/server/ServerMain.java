package bb.server;

public class ServerMain {
	
	// Set up server. Use Netty?
	
	private static ServerMain server;
	
	private ServerMain() {
		server = this;
		System.out.println("Server"); // TODO implement logging system??
	}
	
	public static void main(String[] arguments) {
		new ServerMain();
	}
	
	public static ServerMain getServerInstance() {
		return server;
	}
	
}
