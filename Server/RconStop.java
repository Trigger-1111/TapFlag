import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.ByteOrder;

public class RconStop {
    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port = 25575;
        String pass = "tapflag_dev";

        try (Socket s = new Socket(host, port)) {
            OutputStream out = s.getOutputStream();
            InputStream  in  = s.getInputStream();

            // Auth packet (type 3)
            sendPacket(out, 1, 3, pass);
            Thread.sleep(500);
            byte[] buf = new byte[256];
            in.read(buf);

            // Check auth response id (should not be -1)
            int respId = ByteBuffer.wrap(buf, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (respId == -1) {
                System.out.println("Auth failed");
                return;
            }

            // Stop command (type 2)
            sendPacket(out, 2, 2, "stop");
            Thread.sleep(500);
            System.out.println("stop sent");
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
