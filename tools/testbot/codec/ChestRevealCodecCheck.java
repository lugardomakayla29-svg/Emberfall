import com.solme.emberfall.network.CloseChestRevealPayload;
import com.solme.emberfall.network.OpenChestRevealPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.util.Arrays;

/**
 * Wire-format checks for the two chest reveal packets (GAME_PLAN row 2.6, server half). They prove the BYTES: a payload survives encode then decode
 * unchanged, nothing is left over, the encoding is deterministic, and a peer cannot make the reader accept an oversized string or a truncated packet.
 * They do NOT prove a packet is sent, received, registered, or that the server acts on it: that is Koda's wiring and the live suite, and no client
 * screen exists.
 *
 * WHERE THIS RUNS: it needs the Minecraft and Fabric jars (RegistryFriendlyByteBuf, EmberfallMod.id), which the CI "math-checks" job does not have, so it
 * lives OUTSIDE relic_math/ on purpose (CI treats a check that does not compile there as a failure). Run it by hand with the full classpath; see
 * tools/testbot/codec/run_codec_check.sh. CI does NOT run it. A reveal with a null RegistryAccess is valid here because these payloads read no registry data.
 */
public class ChestRevealCodecCheck {
    static int fails = 0;
    static int total = 0;

    static void check(String l, boolean ok, String e) {
        total++;
        System.out.println((ok ? "PASS " : "FAIL ") + l + "  " + e);
        if (!ok) {
            fails++;
        }
    }

    static RegistryFriendlyByteBuf buf() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
    }

    static byte[] bytes(RegistryFriendlyByteBuf b) {
        byte[] out = new byte[b.readableBytes()];
        b.getBytes(b.readerIndex(), out);
        return out;
    }

    /** Encode then decode. Returns null (a failed comparison, reported by name) if the codec throws or leaves bytes behind, never an exception out of a check. */
    static OpenChestRevealPayload roundTrip(OpenChestRevealPayload in) {
        try {
            RegistryFriendlyByteBuf b = buf();
            OpenChestRevealPayload.STREAM_CODEC.encode(b, in);
            OpenChestRevealPayload out = OpenChestRevealPayload.STREAM_CODEC.decode(b);
            return b.readableBytes() == 0 ? out : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void main(String[] a) {
        try {
            run();
        } catch (Throwable e) {
            check("run: no check threw an unexpected exception", false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(fails == 0 ? "ALL PASS (" + total + " checks)" : "FAILED " + fails + " of " + total);
        if (fails != 0) {
            System.exit(1);
        }
    }

    static void run() {
        // ---- the open packet (server to client) --------------------------------------------------------------------------------------------
        OpenChestRevealPayload p = new OpenChestRevealPayload(7, "Rare", "Ember Ring", 123456789012L);
        check("open: a payload survives encode then decode unchanged and uses every byte", p.equals(roundTrip(p)), String.valueOf(roundTrip(p)));
        byte[] enc = encode(p);
        // varint(7)=1 byte, "Rare" = 1 length + 4, "Ember Ring" = 1 + 10, long = 8: 1 + 5 + 11 + 8 = 25
        check("open: the encoding is exactly 25 bytes for this payload (1 id + 5 tier + 11 item + 8 seed)", enc.length == 25, "got " + enc.length);
        check("open: encoding the same payload twice gives identical bytes (deterministic)", Arrays.equals(enc, encode(p)), "");
        check("open: the id is the FIRST thing on the wire (varint 7 = 0x07)", enc.length > 0 && enc[0] == 7, "first byte " + (enc.length > 0 ? enc[0] : -1));
        check("open: the seed is the LAST 8 bytes, big-endian", enc.length >= 8 && seedBytesOk(p, enc), "");

        // ---- every field changes the bytes (nothing is dropped) -------------------------------------------------------------------------------
        byte[] base = encode(p);
        check("open: changing only the id changes the bytes", !Arrays.equals(base, encode(new OpenChestRevealPayload(8, "Rare", "Ember Ring", 123456789012L))), "");
        check("open: changing only the tier changes the bytes", !Arrays.equals(base, encode(new OpenChestRevealPayload(7, "Epic", "Ember Ring", 123456789012L))), "");
        check("open: changing only the item changes the bytes", !Arrays.equals(base, encode(new OpenChestRevealPayload(7, "Rare", "Ember Rind", 123456789012L))), "");
        check("open: changing only the seed changes the bytes", !Arrays.equals(base, encode(new OpenChestRevealPayload(7, "Rare", "Ember Ring", 123456789013L))), "");
        check("open: tier and item are not swapped on the wire (decode gives tier=Rare, item=Ember Ring)", roundTrip(p) != null && roundTrip(p).tier().equals("Rare") && roundTrip(p).item().equals("Ember Ring"), "");

        // ---- extreme values -----------------------------------------------------------------------------------------------------------------------
        int[] ids = {0, 1, 127, 128, 16383, 16384, Integer.MAX_VALUE, -1, Integer.MIN_VALUE};
        int badIds = 0;
        for (int id : ids) {
            OpenChestRevealPayload x = new OpenChestRevealPayload(id, "T", "I", 1L);
            if (!x.equals(roundTrip(x))) {
                badIds++;
            }
        }
        check("extreme: ids at every varint size boundary and both int extremes survive", badIds == 0, "bad " + badIds);
        long[] seeds = {0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 0x8000000000000000L, 0x00000000FFFFFFFFL};
        int badSeeds = 0;
        for (long sd : seeds) {
            OpenChestRevealPayload x = new OpenChestRevealPayload(1, "T", "I", sd);
            if (!x.equals(roundTrip(x))) {
                badSeeds++;
            }
        }
        check("extreme: seeds 0, 1, -1 and both long extremes survive", badSeeds == 0, "bad " + badSeeds);
        String unicode = "R\u00e9vel \u2014 \u9f8d \uD83D\uDC09";
        OpenChestRevealPayload u = new OpenChestRevealPayload(3, unicode, unicode, 9L);
        check("extreme: non-ASCII text (accents, a dash, CJK, an emoji) survives unchanged", u.equals(roundTrip(u)), "");
        check("extreme: empty strings survive (the wire allows them; the SERVER refuses to open one)", new OpenChestRevealPayload(1, "", "", 0L).equals(roundTrip(new OpenChestRevealPayload(1, "", "", 0L))), "");
        String max = "x".repeat(OpenChestRevealPayload.MAX_TEXT);
        check("extreme: a string of exactly MAX_TEXT (64) characters survives", new OpenChestRevealPayload(1, max, max, 0L).equals(roundTrip(new OpenChestRevealPayload(1, max, max, 0L))), "");

        // ---- bounds: a peer cannot make us accept too much ---------------------------------------------------------------------------------------------
        check("bound: MAX_TEXT is 64", OpenChestRevealPayload.MAX_TEXT == 64, "");
        String over = "x".repeat(OpenChestRevealPayload.MAX_TEXT + 1);
        check("bound: ENCODING a 65 character tier is refused (the sender cannot write what the reader would refuse)", throwsOnEncode(new OpenChestRevealPayload(1, over, "I", 0L)), "");
        check("bound: ENCODING a 65 character item is refused", throwsOnEncode(new OpenChestRevealPayload(1, "T", over, 0L)), "");
        check("bound: DECODING a hand-built packet with a 65 character tier is refused, not read", throwsOnDecode(forged(over, "I")), "");
        check("bound: DECODING a hand-built packet with a 65 character item is refused", throwsOnDecode(forged("T", over)), "");
        check("bound: a hand-built packet claiming a 10 million byte tier is refused without reading it", throwsOnDecode(hugeClaim()), "");
        check("bound: a hand-built 64 character tier and item is accepted (the boundary is exact)", acceptsForged(max, max), "");

        // ---- truncated and empty packets ----------------------------------------------------------------------------------------------------------------
        byte[] full = encode(p);
        int survived = 0;
        for (int cut = 0; cut < full.length; cut++) {
            RegistryFriendlyByteBuf t = buf();
            t.writeBytes(Arrays.copyOf(full, cut));
            try {
                OpenChestRevealPayload.STREAM_CODEC.decode(t);
                survived++;
            } catch (RuntimeException e) {
                // expected: a truncated packet must not decode
            }
        }
        check("truncated: every shorter prefix of a valid packet (0 to 24 bytes) fails to decode, none yields a payload", survived == 0, "decoded " + survived + " of " + full.length);
        RegistryFriendlyByteBuf junk = buf();
        for (int i = 0; i < 40; i++) {
            junk.writeByte(0xFF);
        }
        check("junk: 40 bytes of 0xFF decode to nothing (or fail), never to a plausible payload", junkRejected(junk), "");

        // ---- the close packet (client to server) -------------------------------------------------------------------------------------------------------------
        CloseChestRevealPayload c = new CloseChestRevealPayload(42);
        check("close: it is exactly one varint (1 byte for id 42)", encodeClose(c).length == 1, "got " + encodeClose(c).length);
        check("close: it survives encode then decode and uses every byte", c.equals(decodeClose(encodeClose(c))), "");
        int badClose = 0;
        for (int id : ids) {
            CloseChestRevealPayload x = new CloseChestRevealPayload(id);
            if (!x.equals(decodeClose(encodeClose(x)))) {
                badClose++;
            }
        }
        check("close: ids at every varint boundary and both int extremes survive", badClose == 0, "bad " + badClose);
        check("close: the record has exactly ONE field, the id, so it cannot carry a tier, an item or a seed", CloseChestRevealPayload.class.getRecordComponents().length == 1 && CloseChestRevealPayload.class.getRecordComponents()[0].getName().equals("revealId") && CloseChestRevealPayload.class.getRecordComponents()[0].getType() == int.class, "");
        check("close: extra bytes a client appends (an attempted answer) are NOT read into the payload; only the id is", appendedAnswerIgnored(), "");
        check("close: an empty packet fails to decode", throwsOnDecodeClose(buf()), "");

        // ---- identity of the two packets -----------------------------------------------------------------------------------------------------------------------
        check("type: the two packets have different ids and the expected paths", !OpenChestRevealPayload.TYPE.id().equals(CloseChestRevealPayload.TYPE.id()) && OpenChestRevealPayload.TYPE.id().getPath().equals("open_chest_reveal") && CloseChestRevealPayload.TYPE.id().getPath().equals("close_chest_reveal"), OpenChestRevealPayload.TYPE.id() + " " + CloseChestRevealPayload.TYPE.id());
        check("type: both are in the emberfall namespace", OpenChestRevealPayload.TYPE.id().getNamespace().equals("emberfall") && CloseChestRevealPayload.TYPE.id().getNamespace().equals("emberfall"), "");
        check("type: the type() method returns each class's own TYPE", p.type() == OpenChestRevealPayload.TYPE && c.type() == CloseChestRevealPayload.TYPE, "");
        check("type: the open packet is not registered under the shrine's id (no clash with an existing channel)", !OpenChestRevealPayload.TYPE.id().getPath().contains("shrine") && !CloseChestRevealPayload.TYPE.id().getPath().contains("shrine"), "");
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------------------------------------------

    static byte[] encode(OpenChestRevealPayload p) {
        try {
            RegistryFriendlyByteBuf b = buf();
            OpenChestRevealPayload.STREAM_CODEC.encode(b, p);
            return bytes(b);
        } catch (RuntimeException e) {
            return new byte[0];
        }
    }

    static boolean seedBytesOk(OpenChestRevealPayload p, byte[] all) {
        long v = 0;
        for (int i = all.length - 8; i < all.length; i++) {
            v = (v << 8) | (all[i] & 0xFFL);
        }
        return v == p.seed();
    }

    static boolean throwsOnEncode(OpenChestRevealPayload p) {
        try {
            OpenChestRevealPayload.STREAM_CODEC.encode(buf(), p);
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** A packet a hostile client could build by hand: id 1, then these two strings written WITHOUT the sender-side length limit, then a seed. */
    static RegistryFriendlyByteBuf forged(String tier, String item) {
        RegistryFriendlyByteBuf b = buf();
        b.writeVarInt(1);
        b.writeUtf(tier, 1000);
        b.writeUtf(item, 1000);
        b.writeLong(0L);
        return b;
    }

    static RegistryFriendlyByteBuf hugeClaim() {
        RegistryFriendlyByteBuf b = buf();
        b.writeVarInt(1);
        b.writeVarInt(10_000_000);
        b.writeBytes(new byte[16]);
        return b;
    }

    static boolean throwsOnDecode(RegistryFriendlyByteBuf b) {
        try {
            OpenChestRevealPayload.STREAM_CODEC.decode(b);
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    static boolean acceptsForged(String tier, String item) {
        try {
            OpenChestRevealPayload got = OpenChestRevealPayload.STREAM_CODEC.decode(forged(tier, item));
            return got.tier().equals(tier) && got.item().equals(item);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean junkRejected(RegistryFriendlyByteBuf junk) {
        try {
            OpenChestRevealPayload got = OpenChestRevealPayload.STREAM_CODEC.decode(junk);
            return false && got != null;
        } catch (RuntimeException e) {
            return true;
        }
    }

    static byte[] encodeClose(CloseChestRevealPayload c) {
        try {
            RegistryFriendlyByteBuf b = buf();
            CloseChestRevealPayload.STREAM_CODEC.encode(b, c);
            return bytes(b);
        } catch (RuntimeException e) {
            return new byte[0];
        }
    }

    /** Null (a named failed comparison) if the codec throws or leaves bytes behind. */
    static CloseChestRevealPayload decodeClose(byte[] data) {
        try {
            RegistryFriendlyByteBuf b = buf();
            b.writeBytes(data);
            CloseChestRevealPayload out = CloseChestRevealPayload.STREAM_CODEC.decode(b);
            return b.readableBytes() == 0 ? out : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static boolean appendedAnswerIgnored() {
        try {
            RegistryFriendlyByteBuf extra = buf();
            extra.writeVarInt(5);
            extra.writeUtf("Legendary");
            CloseChestRevealPayload got = CloseChestRevealPayload.STREAM_CODEC.decode(extra);
            return got.revealId() == 5 && extra.readableBytes() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean throwsOnDecodeClose(RegistryFriendlyByteBuf b) {
        try {
            CloseChestRevealPayload.STREAM_CODEC.decode(b);
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }
}
