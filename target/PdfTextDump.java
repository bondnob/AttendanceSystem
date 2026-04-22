import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
public class PdfTextDump {
  public static void main(String[] args) throws Exception {
    PdfReader r = new PdfReader(args[0]);
    PdfTextExtractor ex = new PdfTextExtractor(r);
    System.out.println(ex.getTextFromPage(1));
    r.close();
  }
}
