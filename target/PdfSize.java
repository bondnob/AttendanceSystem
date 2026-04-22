import com.lowagie.text.pdf.PdfReader;
public class PdfSize {
  public static void main(String[] args) throws Exception {
    for (String path : args) {
      PdfReader r = new PdfReader(path);
      var rect = r.getPageSize(1);
      System.out.println(path + " => " + rect.getWidth() + " x " + rect.getHeight());
      r.close();
    }
  }
}
