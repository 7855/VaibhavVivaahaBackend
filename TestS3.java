import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
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
            BasicAWSCredentials creds = new BasicAWSCredentials(
                "e818b85aaae46c7c27472f9f91e4574a", 
                "fd068470c6270813a6eac3c95eb543a72985a41980e8b05473114a8355140f87"
            );
            
            AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                    "https://20c9765c33bcaa6120751cda0fdbb65d.r2.cloudflarestorage.com", 
                    "us-east-1"
                ))
                .withCredentials(new AWSStaticCredentialsProvider(creds))
                .withPathStyleAccessEnabled(true)
                .build();
                
            byte[] data = new byte[600000];
            Arrays.fill(data, (byte) 'A');
            File tempFile = File.createTempFile("test", ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(data);
            }
            
            System.out.println("Uploading file to bucket: vvmapp-images");
            s3.putObject(new PutObjectRequest("vvmapp-images", "test.txt", tempFile));
            String fileUrl = s3.getUrl("vvmapp-images", "test.txt").toString();
            System.out.println("Upload 600KB file successful!");
            System.out.println("Generated URL: " + fileUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

