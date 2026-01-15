package ca.openosp.openo.flowsheets;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import javax.xml.stream.XMLStreamReader;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.xml.stream.XMLInputStream;
import org.apache.xmlbeans.xml.stream.XMLStreamException;
import org.w3c.dom.Node;

public interface FlowsheetDocument extends XmlObject {
   SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(FlowsheetDocument.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.sB2AF798ADE52EA1BCFFBCA28E4F3F101").resolveHandle("flowsheetb059doctype");

   Flowsheet getFlowsheet();

   void setFlowsheet(Flowsheet var1);

   Flowsheet addNewFlowsheet();

   public static final class Factory {
      public static FlowsheetDocument newInstance() {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.type, (XmlOptions)null);
      }

      public static FlowsheetDocument newInstance(XmlOptions var0) {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.type, var0);
      }

      public static FlowsheetDocument parse(String var0) throws XmlException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, (XmlOptions)null);
      }

      public static FlowsheetDocument parse(String var0, XmlOptions var1) throws XmlException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, var1);
      }

      public static FlowsheetDocument parse(File var0) throws XmlException, IOException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, (XmlOptions)null);
      }

      public static FlowsheetDocument parse(File var0, XmlOptions var1) throws XmlException, IOException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, var1);
      }

      public static FlowsheetDocument parse(URL var0) throws XmlException, IOException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, (XmlOptions)null);
      }

      public static FlowsheetDocument parse(URL var0, XmlOptions var1) throws XmlException, IOException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, var1);
      }

      public static FlowsheetDocument parse(InputStream var0) throws XmlException, IOException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, (XmlOptions)null);
      }

      public static FlowsheetDocument parse(InputStream var0, XmlOptions var1) throws XmlException, IOException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, var1);
      }

      public static FlowsheetDocument parse(Reader var0) throws XmlException, IOException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, (XmlOptions)null);
      }

      public static FlowsheetDocument parse(Reader var0, XmlOptions var1) throws XmlException, IOException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, var1);
      }

      public static FlowsheetDocument parse(XMLStreamReader var0) throws XmlException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, (XmlOptions)null);
      }

      public static FlowsheetDocument parse(XMLStreamReader var0, XmlOptions var1) throws XmlException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, var1);
      }

      public static FlowsheetDocument parse(Node var0) throws XmlException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, (XmlOptions)null);
      }

      public static FlowsheetDocument parse(Node var0, XmlOptions var1) throws XmlException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, var1);
      }

      /** @deprecated */
      @Deprecated
      public static FlowsheetDocument parse(XMLInputStream var0) throws XmlException, XMLStreamException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, (XmlOptions)null);
      }

      /** @deprecated */
      @Deprecated
      public static FlowsheetDocument parse(XMLInputStream var0, XmlOptions var1) throws XmlException, XMLStreamException {
         return (FlowsheetDocument)XmlBeans.getContextTypeLoader().parse(var0, FlowsheetDocument.type, var1);
      }

      /** @deprecated */
      @Deprecated
      public static XMLInputStream newValidatingXMLInputStream(XMLInputStream var0) throws XmlException, XMLStreamException {
         return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(var0, FlowsheetDocument.type, (XmlOptions)null);
      }

      /** @deprecated */
      @Deprecated
      public static XMLInputStream newValidatingXMLInputStream(XMLInputStream var0, XmlOptions var1) throws XmlException, XMLStreamException {
         return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(var0, FlowsheetDocument.type, var1);
      }

      private Factory() {
      }
   }

   public interface Flowsheet extends XmlObject {
      SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Flowsheet.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.sB2AF798ADE52EA1BCFFBCA28E4F3F101").resolveHandle("flowsheet1136elemtype");

      Indicator[] getIndicatorArray();

      Indicator getIndicatorArray(int var1);

      int sizeOfIndicatorArray();

      void setIndicatorArray(Indicator[] var1);

      void setIndicatorArray(int var1, Indicator var2);

      Indicator insertNewIndicator(int var1);

      Indicator addNewIndicator();

      void removeIndicator(int var1);

      Header[] getHeaderArray();

      Header getHeaderArray(int var1);

      int sizeOfHeaderArray();

      void setHeaderArray(Header[] var1);

      void setHeaderArray(int var1, Header var2);

      Header insertNewHeader(int var1);

      Header addNewHeader();

      void removeHeader(int var1);

      Measurement[] getMeasurementArray();

      Measurement getMeasurementArray(int var1);

      int sizeOfMeasurementArray();

      void setMeasurementArray(Measurement[] var1);

      void setMeasurementArray(int var1, Measurement var2);

      Measurement insertNewMeasurement(int var1);

      Measurement addNewMeasurement();

      void removeMeasurement(int var1);

      String getName();

      XmlString xgetName();

      boolean isSetName();

      void setName(String var1);

      void xsetName(XmlString var1);

      void unsetName();

      String getDsRules();

      XmlString xgetDsRules();

      boolean isSetDsRules();

      void setDsRules(String var1);

      void xsetDsRules(XmlString var1);

      void unsetDsRules();

      String getDxcodeTriggers();

      XmlString xgetDxcodeTriggers();

      boolean isSetDxcodeTriggers();

      void setDxcodeTriggers(String var1);

      void xsetDxcodeTriggers(XmlString var1);

      void unsetDxcodeTriggers();

      String getDisplayName();

      XmlString xgetDisplayName();

      boolean isSetDisplayName();

      void setDisplayName(String var1);

      void xsetDisplayName(XmlString var1);

      void unsetDisplayName();

      String getWarningColour();

      XmlString xgetWarningColour();

      boolean isSetWarningColour();

      void setWarningColour(String var1);

      void xsetWarningColour(XmlString var1);

      void unsetWarningColour();

      String getRecommendationColour();

      XmlString xgetRecommendationColour();

      boolean isSetRecommendationColour();

      void setRecommendationColour(String var1);

      void xsetRecommendationColour(XmlString var1);

      void unsetRecommendationColour();

      String getTopHTML();

      XmlString xgetTopHTML();

      boolean isSetTopHTML();

      void setTopHTML(String var1);

      void xsetTopHTML(XmlString var1);

      void unsetTopHTML();

      public static final class Factory {
         public static Flowsheet newInstance() {
            return (Flowsheet)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.type, (XmlOptions)null);
         }

         public static Flowsheet newInstance(XmlOptions var0) {
            return (Flowsheet)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.type, var0);
         }

         private Factory() {
         }
      }

      public interface Header extends XmlObject {
         SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Header.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.sB2AF798ADE52EA1BCFFBCA28E4F3F101").resolveHandle("headerd45felemtype");

         Item[] getItemArray();

         Item getItemArray(int var1);

         int sizeOfItemArray();

         void setItemArray(Item[] var1);

         void setItemArray(int var1, Item var2);

         Item insertNewItem(int var1);

         Item addNewItem();

         void removeItem(int var1);

         String getDisplayName();

         XmlString xgetDisplayName();

         boolean isSetDisplayName();

         void setDisplayName(String var1);

         void xsetDisplayName(XmlString var1);

         void unsetDisplayName();

         public static final class Factory {
            public static Header newInstance() {
               return (Header)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.type, (XmlOptions)null);
            }

            public static Header newInstance(XmlOptions var0) {
               return (Header)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.type, var0);
            }

            private Factory() {
            }
         }

         public interface Item extends XmlObject {
            SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Item.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.sB2AF798ADE52EA1BCFFBCA28E4F3F101").resolveHandle("item0b90elemtype");

            Rules getRules();

            boolean isSetRules();

            void setRules(Rules var1);

            Rules addNewRules();

            void unsetRules();

            Ruleset getRuleset();

            boolean isSetRuleset();

            void setRuleset(Ruleset var1);

            Ruleset addNewRuleset();

            void unsetRuleset();

            String getMeasurementType();

            XmlString xgetMeasurementType();

            boolean isSetMeasurementType();

            void setMeasurementType(String var1);

            void xsetMeasurementType(XmlString var1);

            void unsetMeasurementType();

            String getDisplayName();

            XmlString xgetDisplayName();

            boolean isSetDisplayName();

            void setDisplayName(String var1);

            void xsetDisplayName(XmlString var1);

            void unsetDisplayName();

            String getGuideline();

            XmlString xgetGuideline();

            boolean isSetGuideline();

            void setGuideline(String var1);

            void xsetGuideline(XmlString var1);

            void unsetGuideline();

            String getGraphable();

            XmlString xgetGraphable();

            boolean isSetGraphable();

            void setGraphable(String var1);

            void xsetGraphable(XmlString var1);

            void unsetGraphable();

            String getValueName();

            XmlString xgetValueName();

            boolean isSetValueName();

            void setValueName(String var1);

            void xsetValueName(XmlString var1);

            void unsetValueName();

            String getDsRules();

            XmlString xgetDsRules();

            boolean isSetDsRules();

            void setDsRules(String var1);

            void xsetDsRules(XmlString var1);

            void unsetDsRules();

            String getPreventionType();

            XmlString xgetPreventionType();

            boolean isSetPreventionType();

            void setPreventionType(String var1);

            void xsetPreventionType(XmlString var1);

            void unsetPreventionType();

            public static final class Factory {
               public static Item newInstance() {
                  return (Item)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.Item.type, (XmlOptions)null);
               }

               public static Item newInstance(XmlOptions var0) {
                  return (Item)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.Item.type, var0);
               }

               private Factory() {
               }
            }

            public interface Rules extends XmlObject {
               SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Rules.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.sB2AF798ADE52EA1BCFFBCA28E4F3F101").resolveHandle("rules52f3elemtype");

               Recommendation[] getRecommendationArray();

               Recommendation getRecommendationArray(int var1);

               int sizeOfRecommendationArray();

               void setRecommendationArray(Recommendation[] var1);

               void setRecommendationArray(int var1, Recommendation var2);

               Recommendation insertNewRecommendation(int var1);

               Recommendation addNewRecommendation();

               void removeRecommendation(int var1);

               public static final class Factory {
                  public static Rules newInstance() {
                     return (Rules)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.Item.Rules.type, (XmlOptions)null);
                  }

                  public static Rules newInstance(XmlOptions var0) {
                     return (Rules)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.Item.Rules.type, var0);
                  }

                  private Factory() {
                  }
               }

               public interface Recommendation extends XmlObject {
                  SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Recommendation.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.sB2AF798ADE52EA1BCFFBCA28E4F3F101").resolveHandle("recommendation6448elemtype");

                  Condition getCondition();

                  void setCondition(Condition var1);

                  Condition addNewCondition();

                  String getStrength();

                  XmlString xgetStrength();

                  boolean isSetStrength();

                  void setStrength(String var1);

                  void xsetStrength(XmlString var1);

                  void unsetStrength();

                  public interface Condition extends XmlString {
                     SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Condition.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.sB2AF798ADE52EA1BCFFBCA28E4F3F101").resolveHandle("condition42a1elemtype");

                     String getType();

                     XmlString xgetType();

                     boolean isSetType();

                     void setType(String var1);

                     void xsetType(XmlString var1);

                     void unsetType();

                     String getParam();

                     XmlString xgetParam();

                     boolean isSetParam();

                     void setParam(String var1);

                     void xsetParam(XmlString var1);

                     void unsetParam();

                     String getValue();

                     XmlString xgetValue();

                     boolean isSetValue();

                     void setValue(String var1);

                     void xsetValue(XmlString var1);

                     void unsetValue();

                     public static final class Factory {
                        public static Condition newInstance() {
                           return (Condition)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.Item.Rules.Recommendation.Condition.type, (XmlOptions)null);
                        }

                        public static Condition newInstance(XmlOptions var0) {
                           return (Condition)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.Item.Rules.Recommendation.Condition.type, var0);
                        }

                        private Factory() {
                        }
                     }
                  }

                  public static final class Factory {
                     public static Recommendation newInstance() {
                        return (Recommendation)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.Item.Rules.Recommendation.type, (XmlOptions)null);
                     }

                     public static Recommendation newInstance(XmlOptions var0) {
                        return (Recommendation)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.Item.Rules.Recommendation.type, var0);
                     }

                     private Factory() {
                     }
                  }
               }
            }

            public interface Ruleset extends XmlObject {
               SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Ruleset.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.sB2AF798ADE52EA1BCFFBCA28E4F3F101").resolveHandle("ruleset0be2elemtype");

               Rule[] getRuleArray();

               Rule getRuleArray(int var1);

               int sizeOfRuleArray();

               void setRuleArray(Rule[] var1);

               void setRuleArray(int var1, Rule var2);

               Rule insertNewRule(int var1);

               Rule addNewRule();

               void removeRule(int var1);

               public static final class Factory {
                  public static Ruleset newInstance() {
                     return (Ruleset)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.Item.Ruleset.type, (XmlOptions)null);
                  }

                  public static Ruleset newInstance(XmlOptions var0) {
                     return (Ruleset)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.Item.Ruleset.type, var0);
                  }

                  private Factory() {
                  }
               }

               public interface Rule extends XmlObject {
                  SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Rule.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.sB2AF798ADE52EA1BCFFBCA28E4F3F101").resolveHandle("rule417aelemtype");

                  Condition[] getConditionArray();

                  Condition getConditionArray(int var1);

                  int sizeOfConditionArray();

                  void setConditionArray(Condition[] var1);

                  void setConditionArray(int var1, Condition var2);

                  Condition insertNewCondition(int var1);

                  Condition addNewCondition();

                  void removeCondition(int var1);

                  String getIndicationColor();

                  XmlString xgetIndicationColor();

                  boolean isSetIndicationColor();

                  void setIndicationColor(String var1);

                  void xsetIndicationColor(XmlString var1);

                  void unsetIndicationColor();

                  public interface Condition extends XmlString {
                     SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Condition.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.sB2AF798ADE52EA1BCFFBCA28E4F3F101").resolveHandle("condition88d3elemtype");

                     String getType();

                     XmlString xgetType();

                     boolean isSetType();

                     void setType(String var1);

                     void xsetType(XmlString var1);

                     void unsetType();

                     String getParam();

                     XmlString xgetParam();

                     boolean isSetParam();

                     void setParam(String var1);

                     void xsetParam(XmlString var1);

                     void unsetParam();

                     String getValue();

                     XmlString xgetValue();

                     boolean isSetValue();

                     void setValue(String var1);

                     void xsetValue(XmlString var1);

                     void unsetValue();

                     public static final class Factory {
                        public static Condition newInstance() {
                           return (Condition)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.Item.Ruleset.Rule.Condition.type, (XmlOptions)null);
                        }

                        public static Condition newInstance(XmlOptions var0) {
                           return (Condition)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.Item.Ruleset.Rule.Condition.type, var0);
                        }

                        private Factory() {
                        }
                     }
                  }

                  public static final class Factory {
                     public static Rule newInstance() {
                        return (Rule)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.Item.Ruleset.Rule.type, (XmlOptions)null);
                     }

                     public static Rule newInstance(XmlOptions var0) {
                        return (Rule)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Header.Item.Ruleset.Rule.type, var0);
                     }

                     private Factory() {
                     }
                  }
               }
            }
         }
      }

      public interface Indicator extends XmlString {
         SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Indicator.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.sB2AF798ADE52EA1BCFFBCA28E4F3F101").resolveHandle("indicatorefb1elemtype");

         String getKey();

         XmlString xgetKey();

         boolean isSetKey();

         void setKey(String var1);

         void xsetKey(XmlString var1);

         void unsetKey();

         String getColour();

         XmlString xgetColour();

         boolean isSetColour();

         void setColour(String var1);

         void xsetColour(XmlString var1);

         void unsetColour();

         public static final class Factory {
            public static Indicator newInstance() {
               return (Indicator)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Indicator.type, (XmlOptions)null);
            }

            public static Indicator newInstance(XmlOptions var0) {
               return (Indicator)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Indicator.type, var0);
            }

            private Factory() {
            }
         }
      }

      public interface Measurement extends XmlObject {
         SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Measurement.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.sB2AF798ADE52EA1BCFFBCA28E4F3F101").resolveHandle("measurement255eelemtype");

         ValidationRule getValidationRule();

         void setValidationRule(ValidationRule var1);

         ValidationRule addNewValidationRule();

         String getType();

         XmlString xgetType();

         boolean isSetType();

         void setType(String var1);

         void xsetType(XmlString var1);

         void unsetType();

         String getTypeDesc();

         XmlString xgetTypeDesc();

         boolean isSetTypeDesc();

         void setTypeDesc(String var1);

         void xsetTypeDesc(XmlString var1);

         void unsetTypeDesc();

         String getTypeDisplayName();

         XmlString xgetTypeDisplayName();

         boolean isSetTypeDisplayName();

         void setTypeDisplayName(String var1);

         void xsetTypeDisplayName(XmlString var1);

         void unsetTypeDisplayName();

         String getMeasuringInstrc();

         XmlString xgetMeasuringInstrc();

         boolean isSetMeasuringInstrc();

         void setMeasuringInstrc(String var1);

         void xsetMeasuringInstrc(XmlString var1);

         void unsetMeasuringInstrc();

         public static final class Factory {
            public static Measurement newInstance() {
               return (Measurement)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Measurement.type, (XmlOptions)null);
            }

            public static Measurement newInstance(XmlOptions var0) {
               return (Measurement)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Measurement.type, var0);
            }

            private Factory() {
            }
         }

         public interface ValidationRule extends XmlString {
            SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(ValidationRule.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.sB2AF798ADE52EA1BCFFBCA28E4F3F101").resolveHandle("validationrule634felemtype");

            String getName();

            XmlString xgetName();

            boolean isSetName();

            void setName(String var1);

            void xsetName(XmlString var1);

            void unsetName();

            String getRegularExp();

            XmlString xgetRegularExp();

            boolean isSetRegularExp();

            void setRegularExp(String var1);

            void xsetRegularExp(XmlString var1);

            void unsetRegularExp();

            String getMaxValue();

            XmlString xgetMaxValue();

            boolean isSetMaxValue();

            void setMaxValue(String var1);

            void xsetMaxValue(XmlString var1);

            void unsetMaxValue();

            String getMinValue();

            XmlString xgetMinValue();

            boolean isSetMinValue();

            void setMinValue(String var1);

            void xsetMinValue(XmlString var1);

            void unsetMinValue();

            String getIsDate();

            XmlString xgetIsDate();

            boolean isSetIsDate();

            void setIsDate(String var1);

            void xsetIsDate(XmlString var1);

            void unsetIsDate();

            String getIsNumeric();

            XmlString xgetIsNumeric();

            boolean isSetIsNumeric();

            void setIsNumeric(String var1);

            void xsetIsNumeric(XmlString var1);

            void unsetIsNumeric();

            String getMaxLength();

            XmlString xgetMaxLength();

            boolean isSetMaxLength();

            void setMaxLength(String var1);

            void xsetMaxLength(XmlString var1);

            void unsetMaxLength();

            String getMinLength();

            XmlString xgetMinLength();

            boolean isSetMinLength();

            void setMinLength(String var1);

            void xsetMinLength(XmlString var1);

            void unsetMinLength();

            public static final class Factory {
               public static ValidationRule newInstance() {
                  return (ValidationRule)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Measurement.ValidationRule.type, (XmlOptions)null);
               }

               public static ValidationRule newInstance(XmlOptions var0) {
                  return (ValidationRule)XmlBeans.getContextTypeLoader().newInstance(FlowsheetDocument.Flowsheet.Measurement.ValidationRule.type, var0);
               }

               private Factory() {
               }
            }
         }
      }
   }
}
