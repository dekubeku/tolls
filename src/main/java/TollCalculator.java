import de.jollyday.Holiday;
import de.jollyday.HolidayManager;
import de.jollyday.parameter.UrlManagerParameter;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TollCalculator {

  /**
   * Calculate the total toll fee for one day
   *
   * @param vehicle - the vehicle
   * @param dates   - date and time of all passes on one day
   * @return - the total toll fee for that day
   */

  public int getTotalTollFee(Vehicle vehicle, Date... dates) {

    int totalFee = 0;

    //check for free vehicle, if there are no passes that day, or if the date is tollFree
    if(isTollFreeVehicle(vehicle) || dates.length == 0 || isTollFreeDate(dates[0]))
      return 0;

    //Sort the list (assuming it is not) and remove all passes where it was free
    Stream<Date> stream = Stream.of(dates);
    List<Date> listDates = stream.takeWhile(date -> (getTollFee(date) > 0)).sorted().collect(Collectors.toList());

    //check if list is empty/or only 1 element after sorting & removing free passes
    if(listDates.size() == 0)
      return 0;
    else if(listDates.size() == 1)
      return getTollFee(listDates.get(0));


    Iterator iterator = listDates.iterator();

    //Time vars for calculating time diff
    TimeUnit timeUnit = TimeUnit.MINUTES;
    long diffInMillies, minutes;

    //date is the current Date in the iterator-loop
    //prevDate is the Date from which the 60-minute time period starts
    Date date;
    Date prevDate = (Date) iterator.next();

    int currentFee;
    int currentMaxFee = getTollFee(prevDate);

    //Iterate through the list of dates that have a fee
    while(iterator.hasNext()) {
      date = (Date) iterator.next();
      currentFee = getTollFee(date);

      //calculate time diff
      diffInMillies = date.getTime() - prevDate.getTime();
      minutes = timeUnit.convert(diffInMillies, TimeUnit.MILLISECONDS);

      //If this pass happened less than 60 minutes from the previous ..
      //Check if the currentFee is bigger than the max. If it is, assign the fee to currentMax.
      if(minutes <= 60) {
        if (currentFee > currentMaxFee)
          currentMaxFee = currentFee;
      }
      //if they are not in the same 60 minute interval, add together the previous max and the current fee to the total
      //reset the currentMax & the element prevDate to the current date
      else {
        totalFee += currentFee + currentMaxFee;
        currentMaxFee = 0;
        prevDate = date;
      }
    }

    //If all the dates in the list are within the same 60 minute interval, return the currentMaxFee
    if(totalFee == 0 && currentMaxFee > 0)
      totalFee = currentMaxFee;

    //If the total exceeds 60 kr, return 60
    //else the actual total
    if(totalFee > 60)
      return 60;
    else return totalFee;

  }



  private boolean isTollFreeVehicle(Vehicle vehicle) {
    String type = vehicle.getType();
    boolean isTollFree = false;

    switch (type)
    {
      case "Motorbike": case "Tractor": case "Emergency":
      case "Diplomat": case "Foreign": case "Military":
        isTollFree = true;
        break;

      default:
        break;
    }
    return isTollFree;
  }


  public int getTollFee(final Date date) {

    LocalTime time = LocalTime.parse(date.toString().substring(11,16));
    System.out.println(time.toString());

    //Gather all time intervals into groups that have the same fee
    HashMap<LocalTime, LocalTime> group9 = new HashMap<>();
    HashMap<LocalTime, LocalTime> group16 = new HashMap<>();
    HashMap<LocalTime, LocalTime> group22 = new HashMap<>();

    //Populate the hashmaps with the intervals for each toll fee
    //Non-inclusive, so time intervals are one plus/minus 1
    group9.put(LocalTime.parse("05:59"),LocalTime.parse("06:30"));
    group9.put(LocalTime.parse("08:29"),LocalTime.parse("15:00"));
    group9.put(LocalTime.parse("17:59"),LocalTime.parse("18:30"));

    group16.put(LocalTime.parse("06:29"),LocalTime.parse("07:00"));
    group16.put(LocalTime.parse("07:59"),LocalTime.parse("08:30"));
    group16.put(LocalTime.parse("14:59"),LocalTime.parse("15:30"));
    group16.put(LocalTime.parse("16:59"),LocalTime.parse("18:00"));

    group22.put(LocalTime.parse("06:59"),LocalTime.parse("08:00"));
    group22.put(LocalTime.parse("15:29"),LocalTime.parse("17:00"));


    //For each interval in each group, check if the time is in that interval
    //Return corresponding fee
    for(LocalTime start : group9.keySet())
      if(isInInterval(start, group9.get(start),time))
        return 9;

    for(LocalTime start : group16.keySet())
      if(isInInterval(start, group16.get(start),time))
        return 16;

    for(LocalTime start : group22.keySet())
      if(isInInterval(start, group22.get(start),time))
        return 22;

    return 0;

  }

  //Checks if time is in between given start and stop time interval
  private boolean isInInterval(LocalTime start, LocalTime stop, LocalTime time)
  {
    return time.isAfter(start) && time.isBefore(stop);
  }


  private Boolean isTollFreeDate(Date date) {

    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);

    //First check if date is on sat/sun or in july
    if(calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
            calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ||
            calendar.get(Calendar.MONTH) == Calendar.JULY )
      return true;

    //Add 0 in front of date if less than 2 chars
    String dateString = "" + calendar.get(Calendar.DATE);
    if(calendar.get(Calendar.DATE) < 10)
      dateString = "0" + calendar.get(Calendar.DATE);

    //Since Calendar.MONTH goes from 0-11, need to add 1 to get the proper number
    int month = calendar.get(Calendar.MONTH) + 1;

    //Add 0 in front of month if less than 2 chars
    String monthString = "" + month;
    if(month < 10)
      monthString = "0" + month;

    //Make date into localdate to compare with the LocalDate for each Holiday
    LocalDate localDate = LocalDate.parse(
                    calendar.get(Calendar.YEAR) + "-" +
                    monthString + "-" +
                    dateString);

    //Url to XML-file containing the holidays
    URL url = TollCalculator.class.getResource("Holidays.xml");

    UrlManagerParameter urlManParam = new UrlManagerParameter(url, new Properties());
    HolidayManager m = HolidayManager.getInstance(urlManParam);

    //Set with all holidays
    Set<Holiday> holidays = m.getHolidays(calendar.get(Calendar.YEAR));

    //Check if date is a holiday
    for(Holiday h: holidays)
      if(h.getDate().isEqual(localDate))
        return true;

   return false;

  }

}