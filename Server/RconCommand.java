import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.ByteOrder;

public class RconCommand {
    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port = 25575;
        String pass = "tapflag_dev";
        String cmd  = args.length > 0 ? args[0] : "list";

        try (Socket s = new Socket(host, port)) {
            OutputStream out = s.getOutputStream();
            InputStream  in  = s.getInputStream();

            sendPacket(out, 1, 3, pass);
            Thread.sleep(400);
            byte[] buf = new byte[256];
            int n = in.read(buf);
            if (n < 8) { System.out.println("auth read error"); return; }
            int respId = ByteBuffer.wrap(buf, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (respId == -1) { System.out.println("auth failed"); return; }

            sendPacket(out, 2, 2, cmd);
            Thread.sleep(400);
            n = in.read(buf);
            if (n > 12) {
                String response = new String(buf, 12, n - 14, "UTF-8");
                if (!response.isEmpty()) System.out.println(response);
            }
            System.out.println("ok");
        }
    }

    static void sendPacket(OutputStream out, int id, int type, String msg) throws IOException {
        byte[] body = msg.getBytes("UTF-8");
        int len = 4 + 4 + body.length + 2;
        ByteBuffer buf = ByteBuffer.allocate(4 + len);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(len);
        buf.putInt(id);
        buf.putInt(type);
        buf.put(body);
        buf.put((byte)0);
        buf.put((byte)0);
        out.write(buf.array());
        out.flush();
    }
}
