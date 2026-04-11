import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

public class TestS3 {
    public static void main(String[] args) {
        try {
            BasicAWSCredentials creds = new BasicAWSCredentials("AKIAWCV2UPJRIJ2CMA2A", "/MiAl0GfQD4e6iNNaXgYddYPyodIkON5ygfTSGAA");
            AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                .withRegion(Regions.AP_SOUTH_1)
                .withCredentials(new AWSStaticCredentialsProvider(creds))
                .build();
                
            byte[] data = new byte[600000];
            Arrays.fill(data, (byte) 'A');
            File tempFile = File.createTempFile("test", ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(data);
            }
            
            s3.putObject(new PutObjectRequest("uravugal", "test.txt", tempFile));
            System.out.println("Upload 600KB file successful!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
