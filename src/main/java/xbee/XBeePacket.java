
package xbee;
public class XBeePacket {
    private final byte apiIdentifier;
    private final int length;
    private final byte[] data;
    private final byte checksum;
    private byte computed;

    public XBeePacket(byte apiIdentifier, int length, byte[] data, byte checksum) {
        this.apiIdentifier = apiIdentifier;
        this.length = length;
        this.data = data;
        this.checksum = checksum;
    }

    public byte getApiIdentifier() {
        return apiIdentifier;
    }

    public int getLength() {
        return length;
    }

    public byte[] getData() {
        return data;
    }

    public byte getChecksum() {
        return checksum;
    }

    public byte calculateChecksum() {
        byte computed = 0;
        for (int i = 0; i < length - 1; i++) {
            computed += data[i];
        }
        return (byte) (0xFF - computed);
    }

    /**
     * Validate checksum of the data of the packet
     *
     * @return true if the checksum is a match with the data
     */
    public boolean verifyChecksum() {
        byte computed = apiIdentifier;
        for (int i = 0; i < length - 1; i++) {
            computed += data[i];
        }
        computed += checksum;
        
        this.computed = computed;
        return (computed == (byte) 0xFF);
    }
}