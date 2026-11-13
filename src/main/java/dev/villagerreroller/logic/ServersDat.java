package dev.villagerreroller.logic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Adds one server to Minecraft's multiplayer list (servers.dat in the game folder), keeping every server and setting
 * already in it exactly as it was. Plain Java with no Minecraft classes, so it works the same on every version.
 */
public final class ServersDat {
    private ServersDat() {
    }

    /** True when the list already has a server with this address (letter case and a default :25565 port ignored). */
    public static boolean has(byte[] file, String ip) {
        if (file == null || file.length == 0) {
            return false;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(file));
            if (in.readUnsignedByte() != 10) {
                return false;
            }
            in.readUTF();
            while (true) {
                int t = in.readUnsignedByte();
                if (t == 0) {
                    return false;
                }
                String key = in.readUTF();
                if (t == 9 && key.equals("servers")) {
                    int et = in.readUnsignedByte();
                    int n = len(in);
                    if (et != 10) {
                        return false;
                    }
                    for (int i = 0; i < n; i++) {
                        // one server: look for its "ip" string, skip everything else
                        while (true) {
                            int ct = in.readUnsignedByte();
                            if (ct == 0) {
                                break;
                            }
                            String ck = in.readUTF();
                            if (ct == 8 && ck.equals("ip")) {
                                if (same(in.readUTF(), ip)) {
                                    return true;
                                }
                            } else {
                                skip(in, ct, 0);
                            }
                        }
                    }
                    return false;
                }
                skip(in, t, 0);
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    static boolean same(String a, String b) {
        return norm(a).equals(norm(b));
    }

    private static String norm(String s) {
        s = s.trim().toLowerCase(Locale.ROOT);
        return s.endsWith(":25565") ? s.substring(0, s.length() - 6) : s;
    }

    /** The file with the server added at the end of the list (a new file when there was none). */
    public static byte[] withServer(byte[] file, String name, String ip) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        if (file == null || file.length == 0) {
            out.writeUTF("");
            listHeader(out, 1);
            entry(out, name, ip);
            out.writeByte(0);
            out.flush();
            return bytes.toByteArray();
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(file));
        if (in.readUnsignedByte() != 10) {
            throw new IOException("servers.dat isn't an NBT file");
        }
        out.writeUTF(in.readUTF());
        boolean added = false;
        while (true) {
            int t = in.readUnsignedByte();
            if (t == 0) {
                break;
            }
            String key = in.readUTF();
            if (t == 9 && key.equals("servers") && !added) {
                int et = in.readUnsignedByte();
                int n = len(in);
                if (et != 10 && n > 0) {
                    throw new IOException("servers.dat has an unexpected server list");
                }
                listHeader(out, n + 1);
                for (int i = 0; i < n; i++) {
                    copy(in, out, 10, 0);
                }
                entry(out, name, ip);
                added = true;
                continue;
            }
            out.writeByte(t);
            out.writeUTF(key);
            copy(in, out, t, 0);
        }
        if (!added) {
            listHeader(out, 1);
            entry(out, name, ip);
        }
        out.writeByte(0);
        out.flush();
        return bytes.toByteArray();
    }

    private static void listHeader(DataOutputStream out, int n) throws IOException {
        out.writeByte(9);
        out.writeUTF("servers");
        out.writeByte(10);
        out.writeInt(n);
    }

    /** One server, as the game writes it: name and address (the game fills in the rest when it shows the list). */
    private static void entry(DataOutputStream out, String name, String ip) throws IOException {
        out.writeByte(8);
        out.writeUTF("name");
        out.writeUTF(name);
        out.writeByte(8);
        out.writeUTF("ip");
        out.writeUTF(ip);
        out.writeByte(0);
    }

    private static void skip(DataInputStream in, int t, int depth) throws IOException {
        copy(in, new DataOutputStream(new ByteArrayOutputStream()), t, depth);
    }

    /** Copies one value of type t unchanged. */
    private static void copy(DataInputStream in, DataOutputStream out, int t, int depth) throws IOException {
        if (depth > 64) {
            throw new IOException("servers.dat is nested too deeply");
        }
        switch (t) {
            case 1: out.writeByte(in.readByte()); break;
            case 2: out.writeShort(in.readShort()); break;
            case 3: out.writeInt(in.readInt()); break;
            case 4: out.writeLong(in.readLong()); break;
            case 5: out.writeFloat(in.readFloat()); break;
            case 6: out.writeDouble(in.readDouble()); break;
            case 7: { int n = len(in); out.writeInt(n); byte[] a = new byte[n]; in.readFully(a); out.write(a); break; }
            case 8: out.writeUTF(in.readUTF()); break;
            case 9: {
                int et = in.readUnsignedByte();
                int n = len(in);
                out.writeByte(et);
                out.writeInt(n);
                for (int i = 0; i < n; i++) {
                    copy(in, out, et, depth + 1);
                }
                break;
            }
            case 10: {
                while (true) {
                    int ct = in.readUnsignedByte();
                    out.writeByte(ct);
                    if (ct == 0) {
                        break;
                    }
                    out.writeUTF(in.readUTF());
                    copy(in, out, ct, depth + 1);
                }
                break;
            }
            case 11: { int n = len(in); out.writeInt(n); for (int i = 0; i < n; i++) { out.writeInt(in.readInt()); } break; }
            case 12: { int n = len(in); out.writeInt(n); for (int i = 0; i < n; i++) { out.writeLong(in.readLong()); } break; }
            default: throw new IOException("servers.dat has an unknown tag " + t);
        }
    }

    private static int len(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 64000000) {
            throw new IOException("servers.dat is broken");
        }
        return n;
    }
}
