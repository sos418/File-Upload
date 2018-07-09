import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.protobuf.ProtobufDecoder;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class AisHandler extends ChannelInboundHandlerAdapter {

    private File file;
    private String FILE_DIRECTORY_PATH = "/Users/sos418/Downloads/server";
    private String FILE_NAME = "";
    private long FILE_SIZE = 0;
    private int OFFSET = 0;
    private String DEST_PATH = "";
    private String AGREE = "AGREE";

    private MessageProto.ResponseMsg.Builder mBuilder;


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Channel Active!!!!!");

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Channel InActive!!!!!");
        //當中斷連線時，假如檔案沒傳完，將檔案刪除
        if (OFFSET != FILE_SIZE){
            file.delete();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 判斷msg是否是RequestMsg
        if (msg instanceof MessageProto.RequestMsg) {
            MessageProto.RequestMsg requestMsg = (MessageProto.RequestMsg) msg;
            System.out.println("MMSI: " + requestMsg.getMmsi());
            System.out.println("File Number: " + requestMsg.getFileNumber());

            DEST_PATH = FILE_DIRECTORY_PATH + File.separator + requestMsg.getMmsi();

//            回傳ResponseMsg(Agree)
            mBuilder = MessageProto.ResponseMsg.newBuilder();
            mBuilder.setMsg(AGREE);
            ctx.writeAndFlush(mBuilder.build());
            mBuilder.clear();

//            更換RequestMsg編碼器
            ChannelPipeline channelPipeline = ctx.pipeline();
            channelPipeline.replace("RequestMsg","MMSIFile", new ProtobufDecoder(MessageProto.MMSIFile.getDefaultInstance()));
            return;
        }

        //判斷msg是否是MMSIFile
        if (msg instanceof MessageProto.MMSIFile){
            System.out.println("----------------------------------------------------------------------------------------");
            MessageProto.MMSIFile mmsiFile = (MessageProto.MMSIFile) msg;
//          第一次讀取檔案名稱和檔案大小
            if (FILE_NAME.equals("") || FILE_SIZE == 0){
                FILE_NAME = mmsiFile.getFileName();
                FILE_SIZE = mmsiFile.getFileSize();
            }
//          判斷是否傳完資料
            if (OFFSET != FILE_SIZE){
                OFFSET = OFFSET + mmsiFile.getOffset();
                System.out.println("目前已經接收到的大小: " + OFFSET);
                System.out.println("檔案大小: " + FILE_SIZE);
            }



            System.out.println("File Name: " + mmsiFile.getFileName());
            System.out.println("File Size: " + mmsiFile.getFileSize());

        }

        MessageProto.MMSIFile mmsiFile = (MessageProto.MMSIFile) msg;
        createFile(DEST_PATH, FILE_NAME, mmsiFile);

        if (OFFSET == FILE_SIZE){
            System.out.println("接收完成");
            OFFSET =0;
            FILE_NAME = "";
            FILE_SIZE = 0;
            mBuilder = MessageProto.ResponseMsg.newBuilder();
            mBuilder.setMsg("AGREE");
            ctx.writeAndFlush(mBuilder.build());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        ctx.close();
    }

    private void createFile(String fileDirectoryPath, String fileName, MessageProto.MMSIFile msg) throws Exception {

        String filePath = fileDirectoryPath +File.separator + fileName;
        file = new File(filePath);
        //判斷檔案資料夾是否存在
        if (!file.getParentFile().exists()){
            file.getParentFile().mkdirs();
        }

        if (!file.exists()) {
            file.createNewFile();
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(msg.getMmsiBytes().toByteArray());
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        FileChannel fileChannel = randomAccessFile.getChannel();

        while (byteBuffer.hasRemaining()){
            fileChannel.position(file.length());
            fileChannel.write(byteBuffer);
        }
        randomAccessFile.close();
        fileChannel.close();


    }
}