import com.brandontoner.ssim.DuplicateHandler;
import com.brandontoner.ssim.StructuralSimilarity;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        StructuralSimilarity.builder()
                            .withThreshold(0.94)
                            .withDeleteFolder("D:\\Pictures")
                            .withKeepFolder("D:\\Users\\brand\\Pictures\\iCloud Photos\\Photos")
                            .withDuplicateHandler(DuplicateHandler.delete())
                            .build()
                            .run();
    }
}
