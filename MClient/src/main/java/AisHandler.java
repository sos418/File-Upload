import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Logger;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;


public class AisHandler extends ChannelInboundHandlerAdapter {

    private File FILE;
    private String MMSI = "10667020";//每艘船的MMSI都不同
    private String FILE_DIRECTORY_PATH = "/Users/sos418/Downloads/client"; //客戶端的資料夾路徑
    private String FILE_PATH = ""; //檔案路徑
    private String[] FILE_LIST; //檔案陣列
    private int FILE_NUMBER = 0; //檔案數量
    private int COUNT = 1; //傳送檔案的計數器
    private final int PAGE_SIZE = 1024 * 8; //記憶體分配大小
    Logger logger = Logger.getLogger(AisHandler.class.getName());

    /**
     * 當通道建立時，先傳送MMSI和檔案數量給伺服器
     *
     * @param ctx
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {

        FILE = new File(FILE_DIRECTORY_PATH);
        FILE_NUMBER = CountFilesInDirectory(FILE_DIRECTORY_PATH);

        MessageProto.RequestMsg.Builder mBuilder = MessageProto.RequestMsg.newBuilder();
        mBuilder.setMmsi(MMSI);
        mBuilder.setFileNumber(FILE_NUMBER);
        ctx.writeAndFlush(mBuilder.build());
        mBuilder.clear();

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        /*  判斷檔案是否傳完，傳完則中斷連線  */
        if (FILE_NUMBER == COUNT - 1) {
            ctx.channel().disconnect();
            return;
        }

        if (msg instanceof MessageProto.ResponseMsg) {
            MessageProto.ResponseMsg responseMsg = (MessageProto.ResponseMsg) msg;
            logger.info("Server Response: " + responseMsg.getMsg());

            FILE_LIST = FILE.list();
            FILE_PATH = FILE + File.separator + FILE_LIST[COUNT - 1];
            logger.info("Server Response: " + FILE_PATH);

            if (responseMsg.getMsg().equals("AGREE")) {
                sendMMSIFile(ctx);
                COUNT++;
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.info("Client is closed.");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        ctx.close();
    }

    /**
     * 計算在資料夾內的檔案數量
     *
     * @param PATH 資料夾路徑
     * @return 檔案數量
     */
    private int CountFilesInDirectory(String PATH) {
        File f = new File(PATH);
        int count = 0;
        for (File file : f.listFiles()) {
            if (file.isFile()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 將ByteBuffer轉成Byte Array(byte[])
     *
     * @param byteBuffer byte緩衝區
     * @return byte陣列
     */
    private static byte[] getByteArrayFromByteBuffer(ByteBuffer byteBuffer) {
        byte[] bytesArray = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytesArray, 0, bytesArray.length);
        return bytesArray;
    }


    /**
     * 傳送MMSIFile
     *
     * @param ctx
     * @throws IOException
     */
    private void sendMMSIFile(ChannelHandlerContext ctx) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(FILE_PATH, "rw");
        FileChannel channel = raf.getChannel();
        ByteBuffer buffer = ByteBuffer.allocate(PAGE_SIZE);

        MessageProto.MMSIFile.Builder gBuilder = MessageProto.MMSIFile.newBuilder();
        gBuilder.setFileName(FILE_LIST[COUNT - 1]);
        gBuilder.setFileSize(raf.length());
        ctx.writeAndFlush(gBuilder.build());
        gBuilder.clear();


        while (-1 != channel.read(buffer)) {
            buffer.flip();
            gBuilder.setMmsiBytes(ByteString.copyFrom(getByteArrayFromByteBuffer(buffer)));
            gBuilder.setOffset(buffer.limit());
            ctx.writeAndFlush(gBuilder.build());
//            System.out.println(buffer.toString());
            buffer.clear();

        }
    }

}