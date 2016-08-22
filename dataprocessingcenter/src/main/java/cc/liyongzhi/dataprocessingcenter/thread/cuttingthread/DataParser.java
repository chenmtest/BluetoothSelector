package cc.liyongzhi.dataprocessingcenter.thread.cuttingthread;


import java.util.concurrent.LinkedBlockingQueue;

/**
 * 将输入流装包。
 * 先调用DataParser.getInstance()
 * 再调用parseHead
 */
public class DataParser {

    private static DataParser parser = null;
    private static int HEAD_LEN = 19;
    private enum HEADER_STATE
    {
        FAIL, FOUND;
    }
    private static HEADER_STATE headerState = HEADER_STATE.FAIL; // 包头捕获状态
    private static HEADER_STATE tailState = HEADER_STATE.FAIL;   // 包捕获状态
    private int index = 0;
    private final int BUFFER_SIZE = 4096;
    private byte[] headBuf = new byte[4];               // 头部缓存区
    private byte[] frameBuf = new byte[BUFFER_SIZE];    // 数据缓存区
    private int confiLength = 0;   //包长度

    private LinkedBlockingQueue<byte[]> mCutDataQueue;

    private DataParser(LinkedBlockingQueue<byte[]> cutDataQueue) {
        mCutDataQueue = cutDataQueue;
    }

    /**
     *  捕获初始化  初始化包头解析状态，缓存区，index
     */
    private void init(HEADER_STATE tail, HEADER_STATE header, int index)
    {
        tailState = tail;
        confiLength=0;
        headerState = header;
        this.index = index;
        headBuf = new byte[4];
        frameBuf = new byte[BUFFER_SIZE];
    }

    private void parseHead(byte in)
    {
        tailState = HEADER_STATE.FAIL;

        frameBuf[0] = frameBuf[1];
        frameBuf[1] = frameBuf[2];
        frameBuf[2] = frameBuf[3];
        frameBuf[3] = in;

        if (frameBuf[0] == 127 && frameBuf[1] == -128 && frameBuf[2] == 127
                && frameBuf[3] == -128)
        {
            headerState = HEADER_STATE.FOUND;
        }
    }

    public void parsePacket(byte in) throws InterruptedException {
        byte[] inByte = new byte[1];
        inByte[0] = in;
        if (headerState == HEADER_STATE.FAIL)
        {
            //find the header
            parseHead(in);
            index = 3;
        }
        else
        {
            //parse the packet;
            parseCompletePacket(inByte);
        }
    }
    private void parseCompletePacket(byte[] inbyte) throws InterruptedException {
        index = index + 1;
        System.arraycopy(inbyte, 0, frameBuf, index, 1);
        if (index == 5)
        {            //0xff无符号
            confiLength =  ((((int)frameBuf[index-1  ])&0xff )*256 + (((int)frameBuf[index  ])&0xff )) - 13;
        }
        //检查是否出现header
        headBuf[0] = headBuf[1];
        headBuf[1] = headBuf[2];
        headBuf[2] = headBuf[3];
        headBuf[3] = inbyte[0];

        if (headBuf[0] == 127 && headBuf[1] == -128 && headBuf[2] == 127
                && headBuf[3] == -128) {
            headerState = HEADER_STATE.FOUND;
            if (index == 4000 + 18 + 4) {
                putPacket();
            } else {
                makeAFakePacketAndPut();
            }
            init(HEADER_STATE.FAIL, HEADER_STATE.FOUND, 3);
            initHeader();
            return;
        }
        if (index > 4080) {
            makeAFakePacketAndPut();
            init(HEADER_STATE.FAIL, HEADER_STATE.FAIL, 0);
        }
        //检查是否出现header结束
    }
    private void putPacket() throws InterruptedException {
        byte[] inQueue = new byte[4000 + HEAD_LEN];
        System.arraycopy(frameBuf, 0, inQueue, 0, confiLength + HEAD_LEN);
        put(inQueue);
    }
    private void makeAFakePacketAndPut() throws InterruptedException {

        byte[] inQueue = new byte[4000 + HEAD_LEN];
        for (int i = 19; i < 4000 + HEAD_LEN; i++) {
            frameBuf[i] = 126;
        }
        System.arraycopy(frameBuf, 0, inQueue, 0, 4000 + HEAD_LEN);
        put(inQueue);
    }
    private void put(byte[] inQueue) throws InterruptedException {
        mCutDataQueue.put(inQueue);
    }
    private void initHeader() {
        frameBuf[0] = 127;
        frameBuf[1] = -128;
        frameBuf[2] = 127;
        frameBuf[3] = -128;
    }
    public static DataParser getInstance(LinkedBlockingQueue<byte[]> cutDataQueue) {
        if (parser == null)
            parser = new DataParser(cutDataQueue);
        return parser;
    }
}