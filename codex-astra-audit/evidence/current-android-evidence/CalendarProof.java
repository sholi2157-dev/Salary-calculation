import java.util.*;
public class CalendarProof {
  static long midnight(int y,int m,int d) { Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Asia/Jerusalem"));c.clear();c.set(y,m-1,d,0,0,0);return c.getTimeInMillis(); }
  public static void main(String[] args) {
    long selected=midnight(2026,3,27), next=midnight(2026,3,28);
    boolean included=next>=selected && next<selected+86_400_000L;
    if (!included) throw new AssertionError("Expected code-path defect missing");
    System.out.println("DST_RANGE_PROOF timezone=Asia/Jerusalem selected=2026-03-27 next=2026-03-28 differenceHours="+(next-selected)/3600000.0+" actualIncluded="+included+" expectedIncluded=false");
    String huge="9".repeat(400);double rate=Double.parseDouble(huge);
    if(Double.isFinite(rate)) throw new AssertionError();
    System.out.println("TIMER_INPUT_PROOF numericDigits=400 parsed="+rate+" WorkShiftState.requireWouldPass="+(Double.isFinite(rate)&&rate>=0));
    long start=1000999L, end=1001000L;
    long ui=(end-start)/1000, stored=end/1000-start/1000;
    System.out.println("TIMER_SECOND_BOUNDARY uiSeconds="+ui+" savedSeconds="+stored);
  }
}
