package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.io.Serializable;
import com.fasterxml.jackson.databind.ObjectMapper;
public class ComparisonModels {
 public static void main(String[] args) throws Exception {
  Map<String,Object> models=new LinkedHashMap<>();
  for(double value:new double[]{99,0,0.05,-0.05,5_000_000,12_500_000,45_000_000,-6_000_000}) {
   var series=new MeasurementChart.Series("Synthetic reading");series.addOrUpdate(new MeasurementChart.Day(Instant.parse("2024-01-15T12:00:00Z")),value);
   var collection=new MeasurementChart.SeriesCollection(); collection.addSeries(series);
   models.put("constant-"+Double.toString(value).replace('-','n').replace('.','_'),MeasurementChart.lines("SYNTHETIC constant "+value,"Days","Units",collection).toConfig());
  }
  for(String pattern:List.of("0.00","0.###")) {
   var series=new MeasurementChart.Series("Synthetic tie values");int i=0;
   for(double value:new double[]{0.125,0.0625,-0.125}) series.addOrUpdate(new MeasurementChart.Day(Instant.parse("2024-01-15T12:00:00Z").plusSeconds(86400L*i++)),value);
   var collection=new MeasurementChart.SeriesCollection();collection.addSeries(series);
   models.put("rounding-"+pattern.replace('.','_').replace('#','x'),MeasurementChart.lines("SYNTHETIC rounding "+pattern,"Days","Units",collection).withShapes().withItemLabels("{2}",pattern,12).toConfig());
  }
  var method=MeasurementGraphAction22Action.class.getDeclaredMethod("actualLabChart",List.class,String.class,String.class,int.class);method.setAccessible(true);
  List<Map<String,Serializable>> values=new ArrayList<>();int i=0;
  for(String result:List.of("<0.1","Negative","137",">100","145")) {
   Map<String,Serializable> row=new LinkedHashMap<>();row.put("result",result);row.put("units","g/L");row.put("testName","Synthetic hemoglobin");row.put("range","");
   row.put("collDateDate",Date.from(Instant.parse("2020-01-15T12:00:00Z").plusSeconds(365L*86400*i++)));values.add(row);
  }
  models.put("qualified-results",((MeasurementChart)method.invoke(null,values,"Synthetic hemoglobin","SYNTHETIC qualified results",10)).toConfig());
  ObjectMapper mapper=new ObjectMapper();Path root=Path.of(args[0]);Files.createDirectories(root);
  for(var entry:models.entrySet()) mapper.writerWithDefaultPrettyPrinter().writeValue(root.resolve(entry.getKey()+".json").toFile(),entry.getValue());
  System.out.println("Exported "+models.size()+" actual Java chart configurations for "+Locale.getDefault());
 }
}
