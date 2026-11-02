/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.MeasurementChart;
import jakarta.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Renders the bundled Chart.js to the existing PNG/JPEG endpoint contract, including print views. */
public final class BrowserChartRenderer {
    private static final Semaphore SLOTS = EFormBrowserPdfService.RENDER_SLOTS;
    private static final Semaphore WAITERS = new Semaphore(4);
    private BrowserChartRenderer() { }

    public static void write(ServletContext context, OutputStream output, MeasurementChart chart,
                             int width, int height, String format) throws IOException {
        if (chart == null || width < 1 || width > 1800 || height < 1 || height > 3200
                || !("png".equals(format) || "jpeg".equals(format))) {
            throw new IllegalArgumentException("Invalid chart image request");
        }
        boolean acquired = false;
        EFormBrowserPdfService.RendererBrowser session = null;
        try {
            if (!WAITERS.tryAcquire()) throw new IOException("Chart renderer is busy");
            try {
                acquired = SLOTS.tryAcquire(30, TimeUnit.SECONDS);
            } finally {
                WAITERS.release();
            }
            if (!acquired) throw new IOException("Chart renderer is busy");
            String library;
            try (InputStream source = context.getResourceAsStream("/library/chartjs/chart.umd.min.js")) {
                if (source == null) throw new IOException("Bundled chart library is unavailable");
                library = new String(source.readAllBytes(), StandardCharsets.UTF_8);
            }
            BrowserOptions options = EFormBrowserPdfService.buildBrowserOptions(
                    EFormBrowserPdfService.resolveChromiumPath(), !EFormBrowserPdfService.sandboxEnabled(), "http://127.0.0.1");
            session = new EFormBrowserPdfService().createDriver(options);
            RenderBrowser browser = session.driver();
            browser.setTimeouts(Duration.ofSeconds(30), Duration.ofSeconds(30));
            browser.get("about:blank");
            browser.executeScript("const s=document.createElement('script'); s.textContent=arguments[0]; document.head.append(s);", library);
            browser.executeScript(DRAW, chart.toConfig(), width, height);
            Map<String, Object> screenshot = browser.executeCdpCommand("Page.captureScreenshot", Map.of(
                    "format", format, "captureBeyondViewport", true,
                    "clip", Map.of("x", 0, "y", 0, "width", width, "height", height, "scale", 1)));
            Object data = screenshot.get("data");
            if (!(data instanceof String encoded) || encoded.isEmpty()) throw new IOException("Chart image is unavailable");
            output.write(Base64.getDecoder().decode(encoded));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Chart rendering interrupted");
        } catch (RuntimeException | io.github.carlos_emr.carlos.utility.PDFGenerationException failure) {
            // Browser errors can include clinical values; expose neither the reply nor its cause.
            throw new IOException("Chart rendering failed");
        } finally {
            EFormBrowserPdfService.teardownQuietly(session);
            if (acquired) SLOTS.release();
        }
    }

    private static final String DRAW = """
            const [spec, width, height] = arguments;
            document.body.replaceChildren();
            document.body.style.cssText='margin:0;background:white';
            const medications=spec.medications || [];
            const main=document.createElement('canvas');
            main.style.display='block';
            main.width=width; main.height=medications.length ? Math.floor(height * .7) : height;
            document.body.append(main);
            const dateLabel=value=>new Date(value).toLocaleDateString('en-CA',{timeZone:spec.timeZone});
            const scales={x:{type:'linear',title:{display:true,text:spec.xLabel},
                ticks:spec.dateAxis ? {callback:dateLabel} : {}},
                y:{type:'linear',beginAtZero:false,title:{display:true,text:spec.yLabel}}};
            if(spec.secondAxis) scales.y2={type:'linear',position:'right',beginAtZero:false,
                grid:{drawOnChartArea:false},title:{display:true,text:spec.secondAxis}};
            const allDates=spec.datasets.flatMap(ds=>ds.data.map(p=>p.x));
            medications.forEach(m=>allDates.push(m.start,m.end));
            if(medications.length && allDates.length) {
                scales.x.min=Math.min(...allDates); scales.x.max=Math.max(...allDates);
            }
            const ranges={id:'referenceRanges',afterDatasetsDraw(chart){
                spec.datasets.forEach((ds,index)=>{
                    const ctx=chart.ctx; ctx.save(); ctx.strokeStyle='green'; ctx.lineWidth=1;
                    if(!ds.referenceRanges) {
                        if(spec.pointLabels!=='none') {
                            ctx.font='8px Helvetica';ctx.textAlign='center';ctx.fillStyle='#333';
                            const elements=chart.getDatasetMeta(index).data;
                            ds.data.forEach((p,i)=>{
                                const element=elements[i];
                                if(!element) return;
                                const value=spec.fixedDecimals ? Number(p.y).toFixed(2) : String(Number(Number(p.y).toFixed(3)));
                                ctx.fillText(value,element.x,element.y-5);
                                if(spec.pointLabels==='dateValue') ctx.fillText(dateLabel(p.x),element.x,element.y-16);
                            });
                        }
                        ctx.restore();return;
                    }
                    ds.data.forEach(p=>{
                        if(p.isRangeEnd) return;
                        const x=chart.scales.x.getPixelForValue(p.x), low=chart.scales.y.getPixelForValue(p.y),
                              high=chart.scales.y.getPixelForValue(p.high);
                        ctx.beginPath();ctx.moveTo(x,low);ctx.lineTo(x,high);
                        ctx.moveTo(x-3,low);ctx.lineTo(x+3,low);ctx.moveTo(x-3,high);ctx.lineTo(x+3,high);ctx.stroke();
                    });ctx.restore();
                });
            }};
            const mainChart=new Chart(main,{type:'scatter',data:{datasets:spec.datasets},plugins:[ranges],
                options:{responsive:false,animation:false,devicePixelRatio:1,scales,
                    plugins:{title:{display:true,text:spec.title},legend:{display:true}}}});
            if(medications.length) {
                const meds=document.createElement('canvas');meds.width=width;meds.height=height-main.height;
                meds.style.display='block';
                document.body.append(meds);
                const names=[...new Set(medications.map(m=>m.name))];
                const medicationChart=new Chart(meds,{type:'bar',data:{labels:names,datasets:[{label:'Meds',
                    data:medications.map(m=>({y:m.name,x:[m.start,m.end]})),backgroundColor:'#4c78a8'}]},
                    options:{indexAxis:'y',responsive:false,animation:false,devicePixelRatio:1,
                        scales:{x:{type:'linear',min:scales.x.min,max:scales.x.max,ticks:{callback:dateLabel},
                            title:{display:true,text:'Date/Time'}},y:{type:'category',labels:names}},
                        plugins:{legend:{display:false}}}});
                // Matching date bounds alone is insufficient: long medication names consume
                // more left-axis space. Align the plot rectangles before producing a print image.
                const panels=[mainChart,medicationChart];
                for(let attempt=0;attempt<3;attempt++) {
                    const left=Math.max(...panels.map(chart=>chart.chartArea.left));
                    const right=Math.min(...panels.map(chart=>chart.chartArea.right));
                    if(panels.every(chart=>Math.abs(chart.chartArea.left-left)<=1 && Math.abs(chart.chartArea.right-right)<=1)) break;
                    panels.forEach(chart=>{
                        const padding=chart.options.layout.padding;
                        chart.options.layout.padding={
                            top:padding.top||0,bottom:padding.bottom||0,
                            left:(padding.left||0)+left-chart.chartArea.left,
                            right:(padding.right||0)+chart.chartArea.right-right};
                        chart.update('none');
                    });
                }
                if(Math.abs(mainChart.chartArea.left-medicationChart.chartArea.left)>1 ||
                   Math.abs(mainChart.chartArea.right-medicationChart.chartArea.right)>1) {
                    throw new Error('Chart date axes could not be aligned');
                }
            }
            return true;
            """;
}
