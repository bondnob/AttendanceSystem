import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfContentReaderTool;
import java.io.PrintWriter;
public class PdfStreamDump {
  public static void main(String[] args) throws Exception {
    PdfReader r = new PdfReader(args[0]);
    PdfContentReaderTool.listContentStreamForPage(r, 1, new PrintWriter(System.out, true));
    r.close();
  }
}
