// labDecisionSupport.js version 1.3 (c) 2017-2019 Peter Hutten-Czapski
// use at your own risk

function AddIfMissingTitle(x,content){
	if (document.getElementById(x).title.indexOf(content)==-1){
			document.getElementById(x).title += content
	}
}

function AddIfMissingStyle(x,content){
	if (document.getElementById(x).style.cssText.indexOf(content)==-1){
			document.getElementById(x).style.cssText += "; "+content
	}
}

function AddIfMissingHTML(x,content, test){
	if (document.getElementById(x).innerHTML.indexOf(test)==-1){
			document.getElementById(x).innerHTML += content
	}
}

function hilite(me){
	 document.getElementById(me).style.backgroundColor='#FDFF47';
	 document.getElementById(me).style.outline = '#f00 solid 2px';
}

function getLines(x){
	var multi=document.getElementById(x).value ;
	var lines=multi.split("\n");
	var numLines=lines.length;
	return numLines;
}


function getLabAge(DateId){
	var num_months = 99;
	//"2012-12-06 00:00:00"
	var strDate=document.getElementById(DateId).value ;
	if (strDate.length >9) {
		var dateParts = (strDate.substring(0,10)).split("-");
		var date = new Date(dateParts[0], (dateParts[1] - 1), dateParts[2]);
		var today= new Date();
		var milli_today=today.getTime();
		var milli_date=date.getTime();
		var diff = milli_today - milli_date;
		num_months = Math.round(diff/2628000000); //(((diff / 1000)s / 60)m / 60)h / 24d / 365y * 12m
	} else {
		document.getElementById(DateId).value="?" ;
	}
	return num_months;
}

function Framingham1991(age, female, smoking, systolic, lipid_ratio, predict_length) {	
	var chd_theta0 		= 0.9145;
 	var chd_theta1 		= -0.2784;
 	var chd_b0		= 15.5305;
 	var chd_b1		= 28.4441;	// female
 	var chd_b2		= -1.4792;	// log(age)
 	var chd_b3		= 0;		// log(age)^2
 	var chd_b4		= -14.4588;	// log(age)*sex
 	var chd_b5		= 1.8515;	// log(age)^2*sex
 	var chd_b6		= -0.9119;	// log(SPB)
 	var chd_b7		= -0.2767;	// smoking
 	var chd_b8		= -0.7181;	// log(total_c/hdl_c)
 
 	var chd_b1out = chd_b1 * female;
 	var chd_b2out = chd_b2 * Math.log(age);
 	var chd_b3out = chd_b3 * Math.log(age) * Math.log(age);
 	var chd_b4out = chd_b4 * Math.log(age) * female;
 	var chd_b5out = chd_b5 * Math.log(age) * Math.log(age) * female;
 	var chd_b6out = chd_b6 * Math.log(systolic);
 	var chd_b7out = chd_b7 * smoking;
 	var chd_b8out = chd_b8 * Math.log(lipid_ratio);
 
 	var mean = chd_b0 + chd_b1out + chd_b2out + chd_b3out + chd_b4out + chd_b5out + chd_b6out + chd_b7out + chd_b8out;
 
 	var log_var = chd_theta0 + chd_theta1 * mean;
 
 	var u = (Math.log(predict_length) - mean ) / Math.exp(log_var);
 
 	var chd_risk = (1.0 - Math.exp( -Math.exp( u ) )) * 0.75;  // the calculation is the simpler 1991 Framingham which is 20-30% high
 
	return chd_risk;
}

// function GetFraminghamCHDRiskPoints(age, sex, smoking, systolic, treated_bp, total_chol, hdl_chol)

function GetFraminghamCHDRiskPoints(age, female, smoking, systolic, treated_bp, chol, hdl_chol){			

    var age_points = 0;
    
    if ( age >= 20 && age <= 34 )
    {
        age_points = (female == 1) ? -7 : -9;
    }
    else if ( age <= 39 )
    {
        age_points = (female == 1) ? -3 : -4;
    }
    else if ( age <= 44 )
    {
        age_points = (female == 1) ? 0 : 0;
    }
    else if ( age <= 49 )
    {
        age_points = (female == 1) ? 3 : 3;
    }
    else if ( age <= 54 )
    {
        age_points = (female == 1) ? 6 : 6;
    }
    else if ( age <= 59 )
    {
        age_points = (female == 1) ? 8 : 8;
    }
    else if ( age <= 64 )
    {
        age_points = (female == 1) ? 10 : 10;
    }
    else if ( age <= 69 )
    {
        age_points = (female == 1) ? 12 : 11;
    }
    else if ( age <= 74 )
    {
        age_points = (female == 1) ? 14 : 12;
    }
    else if ( age <= 79 )
    {
        age_points = (female == 1) ? 16 : 13;
    }
    
    var chol_points = 0;
    var smoking_points = 0;
    
    if ( age >= 20 && age <= 39 )
    {
        if ( smoking == 1 )
            smoking_points = (female == 1) ? 9 : 8;
        if ( chol <= 4.14 )
            chol_points = 0;
        else if ( chol <= 5.19 )
            chol_points = (female == 1) ? 4 : 4;
        else if ( chol <= 6.19 )
            chol_points = (female == 1) ? 8 : 7;
        else if ( chol <= 7.2 )
            chol_points = (female == 1) ? 11 : 9;
        else
            chol_points = (female == 1) ? 13 : 11;
    }
    else if ( age <= 49 )
    {
        if ( smoking == 1 )
            smoking_points = (female == 1) ? 7 : 5;
        
        if ( chol <= 4.14 )
            chol_points = 0;
        else if ( chol <= 5.19 )
            chol_points = (female == 1) ? 3 : 3;
        else if ( chol <= 6.19 )
            chol_points = (female == 1) ? 6 : 5;
        else if ( chol <= 7.2 )
            chol_points = (female == 1) ? 8 : 6;
        else
            chol_points = (female == 1) ? 10 : 8;
    }
    else if ( age <= 59 )
    {
        if ( smoking == 1 )
            smoking_points = (female == 1) ? 4 : 3;
        
        if ( chol <= 4.14 )
            chol_points = 0;
        else if ( chol <= 5.19 )
            chol_points = (female == 1) ? 2 : 2;
        else if ( chol <= 6.19 )
            chol_points = (female == 1) ? 4 : 3;
        else if ( chol <= 7.2 )
            chol_points = (female == 1) ? 5 : 4;
        else
            chol_points = (female == 1) ? 7 : 5;
    }
    else if ( age <= 69 )
    {
        if ( smoking == 1 )
            smoking_points = (female == 1) ? 2 : 1;
        
        if ( chol <= 4.14 )
            chol_points = 0;
        else if ( chol <= 5.19 )
            chol_points = (female == 1) ? 1 : 1;
        else if ( chol <= 6.19 )
            chol_points = (female == 1) ? 2 : 1;
        else if ( chol <= 7.2 )
            chol_points = (female == 1) ? 3 : 2;
        else
            chol_points = (female == 1) ? 4 : 3;
    }
    else if ( age <= 79 )
    {
        if ( smoking == 1 )
            smoking_points = (female == 1) ? 1 : 1;
        
        if ( chol <= 4.14 )
            chol_points = 0;
        else if ( chol <= 5.19 )
            chol_points = (female == 1) ? 1 : 0;
        else if ( chol <= 6.19 )
            chol_points = (female == 1) ? 1 : 0;
        else if ( chol <= 7.2 )
            chol_points = (female == 1) ? 2 : 1;
        else
            chol_points = (female == 1) ? 2 : 1;
    }
    
    var hdl_points = 0;
    
    if ( hdl_chol >= 1.55 )
        hdl_points = -1;
    else if ( hdl_chol > 1.3 )
        hdl_points = 0;
    else if ( hdl_chol > 1.04 )
        hdl_points = 1;
    else
        hdl_points = 2;
    
    var systolic_points = 0;
    
    if ( treated_bp == 0 )
    {
        //untreated bp
        if ( systolic < 120 )
            systolic_points = (female == 1) ? 0 : 0;
        else if ( systolic < 129 ) 
            systolic_points = (female == 1) ? 1 : 0;
        else if ( systolic < 139 ) 
            systolic_points = (female == 1) ? 2 : 1;
        else if ( systolic < 159 ) 
            systolic_points = (female == 1) ? 3 : 1;
        else 
            systolic_points = (female == 1) ? 4 : 2;
    }
    else
    {
        //treated bp        
        if ( systolic < 120 )
            systolic_points = (female == 1) ? 0 : 0;
        else if ( systolic < 129 ) 
            systolic_points = (female == 1) ? 3 : 1;
        else if ( systolic < 139 ) 
            systolic_points = (female == 1) ? 4 : 2;
        else if ( systolic < 159 ) 
            systolic_points = (female == 1) ? 5 : 2;
        else 
            systolic_points = (female == 1) ? 6 : 3;
    }
    
    var total_points = age_points + chol_points + smoking_points + hdl_points + systolic_points;
    
    if ( female == 0 )
    {
        //male
        if ( total_points <= 4 )
            return 0.01;
        else if ( total_points <= 6 )
            return 0.02;
        else if ( total_points <= 7 )
            return 0.03;
        else if ( total_points <= 8 )
            return 0.04;
        else if ( total_points <= 9 )
            return 0.05;
        else if ( total_points <= 10 )
            return 0.06;
        else if ( total_points <= 11 )
            return 0.08;
        else if ( total_points <= 12 )
            return 0.10;
        else if ( total_points <= 13 )
            return 0.12;
        else if ( total_points <= 14 )
            return 0.16;
        else if ( total_points <= 15 )
            return 0.20;
        else if ( total_points <= 16 )
            return 0.25;
        else
            return 0.30;
    }
    else
    {
        //female
        if ( total_points <= 12 )
            return 0.01;
        else if ( total_points <= 14 )
            return 0.02;
        else if ( total_points <= 15 )
            return 0.03;
        else if ( total_points <= 16 )
            return 0.04;
        else if ( total_points <= 17 )
            return 0.05;
        else if ( total_points <= 18 )
            return 0.06;
        else if ( total_points <= 19 )
            return 0.08;
        else if ( total_points <= 20 )
            return 0.11;
        else if ( total_points <= 21 )
            return 0.14;
        else if ( total_points <= 22 )
            return 0.17;
        else if ( total_points <= 23 )
            return 0.22;
        else if ( total_points <= 24 )
            return 0.27;
        else
            return 0.30;
    }
}

function decisionSupport(){

	if (document.getElementById('counter').value.length<1) {
		//The searchbox picks up user input and must be pretty		
		document.getElementById('counter').value='1';
		var history2 = document.getElementById('history2').value;
	        var history2Split = history2.split("]]-----\n");
        	document.getElementById('searchbox').value = history2Split.pop().toUpperCase();
		var meds = document.getElementById('Meds').value.toString();
		var FreeText = /\(.*\)/g;
		var string = meds;
	      	document.getElementById('searchboxRx').value = string.replace(FreeText,"");
		document.getElementById('history2').value= history2Split.join("\n");
	} 
	//dynamically resize based on number of lines
	document.getElementById('searchbox').style.height= 9+getLines('searchbox')*10;
	document.getElementById('searchboxRx').style.height= 9+getLines('searchboxRx')*10;
	
	//The searchbox has only the first bullet of the history and possibly user input
	//all takes all the history items and anything in the disease registry
	var all=document.getElementById('searchbox').value + document.getElementById('history2').value + document.getElementById('dxlist').value;
	var allRx=document.getElementById('searchboxRx').value ;

	function SystolicPressure(){
		var ref=document.getElementById('BP').value.toString(); 
		var mySplitResult = ref.split('/');
		var x= mySplitResult[0];
		return x;
	}

	function HasDM(){
		var FreeText = /Diabet|IDDM|\sDM\s|T2DM|T1DM|type 2 DM|type2 DM|type1 DM|type 1 DM|T2DM|T2 DM|\sDM2\s/i;
		var match = all.search(FreeText);
		var FreeText = /METFORMIN|INSULIN|GLYBURIDE|GLICLAZIDE/i;
		var match2 = allRx.search(FreeText);
		var theA1C = parseFloat(document.getElementById('A1Cvalue').value);
		if( (match != -1)||(match2 != -1)||((theA1C >0.065)&&(theA1C <1))||(theA1C >6.5) ){ 
			return true;
		}
		return false;
	}

	function HasCAD(){
		var FreeText = /ischemi|stent|coronary|CABG/i;
		var match = all.search(FreeText);
		var FreeText = /NITROGLYCERIN|CLOPIDOGREL/i;
		var match2 = allRx.search(FreeText);
		if( (match != -1)||(match2 != -1) ){ 
			return true;
		}
		return false;
	}

	function HasCHF(){
		var FreeText = /CHF|Congestive cardiac|heart failure|LVF|RVF|Systolic dysfunction|Diastolic dysfunction|pulmonary edema|pulmonary oedema|\sHF\s/i;
		var match = all.search(FreeText);
		if(match != -1){ 
			return true;
		}
		return false;
	}

	function HasHTN(){
		var FreeText = /HBP|Hypertension|Elevated BP|\sBP\s|\sHTN\s/i;
		var match = all.search(FreeText);
		if(match != -1){ 
			return true;
		}
		return false;
	}

	function HasCKD(){
		var FreeText = /CKD|Chronic kidney disease|Renal failure|renal insufficiency|\sCRF\s/i;
		var match = all.search(FreeText);
		var estGFR = parseFloat(document.getElementById('eGFRvalue').value); 
		if( (match != -1)||(estGFR < 45) ){ 
			return true;
		}
		return false;
	}

	function OnStatin(){
		var FreeText = /STATIN/i;
		var match2 = allRx.search(FreeText);
		if(match2 != -1){
			return true;
		}
		return false;
	}
	// OnStatin is part of the public API consumed by external lab decision eform JSPs.
	// Static analysis cannot see those call sites, so reference it here to suppress warnings.
	void OnStatin;


// LIPID assessment
// NICE 2010 recommends that all adults 40-74 should have an estimate of CVD risk and if elevated to get a cholesterol.
// If the risk remains above 20% after lifestyle modifications Simvastatin 40mg should be considered.  
// The logic we use here is that adults 35-75 without diabetes, CKD or IHD can be risk assessed by Framingham.  
// 75 is the upper age cut off as some at that age are fit and may benefit and others are infirm and are unlikely to gain
// If at low risk they can have the lipids done q 5 years.  The pretest risk threshold for annual testing is set at 15%.
// This tool just simplifies population based screening, but it is not a substitute for critical thinking
// eg. Framingham does not assess the added risk of low SES 
// nor 
// a family history of premature CHD 
// (a first degree male relative with CHD under age 50 or female relative under age 60 can double the risk).  	


	var age				= parseFloat(document.getElementById("PatientAge").value);
	var sex 			= (document.getElementById("PatientGender").value=='M')?0:1; //1 if female, 0 if male
	var smoking 			= ((document.getElementById('smoker').value == 'Y')||(document.getElementById('dailySmokes').value >0))?1:0; //1 if smoker (or quit within last year), 0 otherwise			
	var systolic 			= SystolicPressure();	
	var treated_bp			= (HasHTN())?1:0;
	var total_chol 			= parseFloat(document.getElementById("TCHLvalue").value);
	var hdl_chol 			= parseFloat(document.getElementById("HDLvalue").value);
	var predict_length 		= 10;
	var lipid_ratio 		= total_chol / hdl_chol;
	var risk 			= 0;
	var threashold 			= 15; //NICE suggests threashold of 20%
	var Diabetic			= (HasDM())?1:0;
	var IHD				= (HasCAD())?1:0;
	var CKD				= (HasCKD())?1:0;
	var cssString			= 'text-align:left; color:red; background:white;'; 
	var cssString2			= 'text-align:left; color:green; background:white;';
	var theA1C			= parseFloat(document.getElementById('A1Cvalue').value);
	var estGFR			= parseFloat(document.getElementById("eGFRvalue").value);

	if (age > 35) {
		// no need to screen young adults at no identified risk: US Preventative Task Force Grade A
		if ( (lipid_ratio)&&(systolic) ) {
			risk = Math.round(GetFraminghamCHDRiskPoints(age, sex, smoking, systolic, treated_bp, total_chol, hdl_chol)*100);
			//risk = Math.round(Framingham1991(age, sex, smoking, systolic, lipid_ratio, predict_length)*100);
			var lipidDate = document.getElementById("TCHLdate").value;
			if ( (lipidDate != "")&&(lipidDate != document.getElementById("m$FRAM#dateObserved").value)&&(!(Diabetic))&&(!(IHD)) ) {
				document.getElementById("m$FRAM#dateObserved").value = lipidDate ;
				document.getElementById("m$FRAM#value").value= risk ;
				add('subject', risk+"% Framingham risk");
			}
		} else {
			//stratify by risk calculated from existing data assuming average to poor bp and lipids
			if (!(systolic)) { systolic=150; }  
			//risk = Math.round(GetFraminghamCHDRiskPoints(age, sex, smoking, systolic, treated_bp, '6', '1')*100);
			risk = Math.round(Framingham1991(age, sex, smoking, systolic, '6', predict_length)*100);
		}

		if (Diabetic) {
			// UKPDS might be better but some argue that it underestimates risk
			risk= 30;  //this is above the threashold
			add('subject','Diabetes ');
		}

		if (IHD) {
			// the subject should be added only based on bloodwork being ordered, but no panel for this
			add('subject','IHD ');
			risk= 30;  //this is above the threashold
		}

		if (CKD) {
			risk= 30;  //this is above the threashold
			add('subject','CKD ');
		}
		if (treated_bp) {
			add('subject','Hypertension ');
		}

	}

	if (risk<threashold){
		//for low risk a Cholesterol every 5 years suffices
		AddIfMissingTitle('LIPID1',"At low ~"+risk+"% 10yr cardiac risk. " );
		if(getLabAge('TCHLdate')<60){
			// thus a fairly recent cholesterol can be greened
			AddIfMissingStyle('TCHLdate',cssString2);
		} else {
			if ( (sex==1)&&(age>50)||(sex==0)&&(age>40) ){
				//a low risk probably could be revaluated at 5 years in case risk changes so red it
				AddIfMissingTitle('LIPID1',"\nBut age "+age+" and no charted cholesterol in the last 5 years.\n" );
				hilite('LIPID1');
				AddIfMissingStyle('TCHLdate',cssString);
			}
		}
	} else {
		if (age>75) { 
			AddIfMissingTitle('LIPID1',"Risk is elevated in the elderly, but there is little evidence for Statins in PRIMARY PREVENTION at age "+age+". " );
			AddIfMissingTitle('LIPID1',"\nTreatment may be beneficial (ARR 0.5-2% SAGE PROSPER) for those with established disease. " );
			if ( (IHD)&&(getLabAge('TCHLdate')>12) ) {
				AddIfMissingTitle('LIPID1',"Known IHD with no charted cholesterol in the last year. " );
				hilite('LIPID1');
				AddIfMissingStyle('TCHLdate',cssString);
			}
		} else {			
			//more agressive and frequent determinations are indicated for those at higher risk
			AddIfMissingTitle('LIPID1',"At ~"+risk+"% 10yr cardiac risk. " );
			if(getLabAge('TCHLdate')>12){
				//red it
				AddIfMissingTitle('LIPID1',"No charted cholesterol in the last year. " );
				hilite('LIPID1');
				AddIfMissingStyle('TCHLdate',cssString);
			} else {
				//green it
				AddIfMissingTitle('LIPID1',"Recent cholesterol done "+getLabAge('TCHLdate')+ " months ago. " );
				AddIfMissingStyle('TCHLdate',cssString2);
			}
		}
	}

	if (Diabetic) {
		AddIfMissingTitle('DM1',"Diabetic with last A1C, "+theA1C+","+getLabAge('A1Cdate')+" months ago" );
		if( (getLabAge('A1Cdate')>6)||( (theA1C >7)&&(getLabAge('A1Cdate')>3) ) ){
			AddIfMissingTitle('DM1',"\nIts worth rechecking now. " );
			hilite('DM1');
			AddIfMissingStyle('A1Cdate',cssString);
		} else {
			AddIfMissingTitle('DM1',"\nA1Cs are not helpful if done more frequently than every three months and if well controlled A1C should be done 6 monthly" );
			AddIfMissingStyle('A1Cdate',cssString2);			
		}
	}

	if( HasCHF()&&( (getLabAge('eGFRdate')>12)||(getLabAge('Napldate')>12) ) ){
		AddIfMissingTitle('CHF1',"CHF with last lytes over 12 months ago. " );
		hilite('CHF1');
		AddIfMissingStyle('eGFRdate',cssString);
	}

	if( HasHTN()&&( (getLabAge('eGFRdate')>12)||(getLabAge('Napldate')>12) ) ){
		AddIfMissingTitle('HBP1',"Hypertension with last lytes over 12 months ago");
		hilite('HBP1');
		AddIfMissingStyle('eGFRdate',cssString);
		AddIfMissingStyle('ACRdate',cssString);
	}
/*  if on statin further cholesterol determinations are not necessary
	if( OnStatin()&&(getLabAge('TCHLdate')>12) ){
		AddIfMissingTitle('LIPID1',"On Statin with last cholesterol over 12 months ago");
		hilite('LIPID1');
		AddIfMissingStyle('TCHLdate',cssString);
	}
*/
	if ( (CKD)&&(getLabAge('eGFRdate')>6) ){
		AddIfMissingTitle('CKD3',"CKD with eGFR of "+estGFR+" with last lytes over 6 months ago");
		hilite('CKD3');
		AddIfMissingStyle('eGFRdate',cssString);
		AddIfMissingStyle('ACRdate',cssString);
	}

	var FreeText = /THYROXINE/i;
	var match = allRx.search(FreeText);
	var FreeText = /THYROID|GOITRE/i;
	var match2 = all.search(FreeText);
	if(((match != -1)||(match2 != -1))&&(getLabAge('TSHdate')>12)){
		AddIfMissingTitle('THYROID1','Known thyroid problem with last TSH over 12 months ago');
		hilite('THYROID1');
	}

	var FreeText = /AMIODARONE/i;
	var match = allRx.search(FreeText);
	if((match != -1)&&((getLabAge('TSHdate')>12)||(getLabAge('ALTdate')>12))){
		AddIfMissingTitle('THYROID1', "On Amiodarone with last bloodwork over 12 months ago");
		AddIfMissingTitle('LFT1', "On Amiodarone with last bloodwork over 12 months ago");
		hilite('THYROID1');
		hilite('LFT1');
	}

	var FreeText = /ISOTRETINOIN/i;
	var match = allRx.search(FreeText);
	if((match != -1)&&((getLabAge('TCHLdate')>1)||(getLabAge('ALTdate')>1))){
		AddIfMissingTitle('LFT1','On Retinoid with last LFTs over a month ago');
		AddIfMissingTitle('LIPID1','On Retinoid with last Lipids over a month ago');
		AddIfMissingTitle('CBC1','On Retinoid with last CBC over a month ago');
		hilite('LFT1');
		hilite('LIPID1');
		hilite('CBC1');
		AddIfMissingStyle('ALTdate',cssString);
	}

	var FreeText = /COUMADIN|WARFARIN/i;
	var match = allRx.search(FreeText);
	if(match != -1){
		AddIfMissingTitle('INR1',"On Warfarin");
		hilite('INR1');
	}

	var FreeText = /AntipsychoticAtypical|risperidone|quetiapine|olanzapine|aripiprazole|clozapine/i;
	var match = allRx.search(FreeText);
	if((match != -1)&&((getLabAge('TCHLdate')>12)||(getLabAge('A1Cdate')>12)||(getLabAge('ALTdate')>12))){
		AddIfMissingTitle('DM1', "On Atypical with last bloodwork over 12 months ago");
		AddIfMissingTitle('LIPID1',"On Atypical with last bloodwork over 12 months ago");
		AddIfMissingTitle('LFT1', "On Atypical with last bloodwork over 12 months ago");
		AddIfMissingTitle('CBC1',"On Atypical with last bloodwork over 12 months ago");
		hilite('DM1');
		hilite('LIPID1');
		hilite('LFT1')
		hilite('CBC1');
		AddIfMissingStyle('A1Cdate',cssString);
		AddIfMissingStyle('ALTdate',cssString);
	}

	var FreeText = /Spironolactone|thiazide|pril|furosemide/i;
	var match = allRx.search(FreeText);
	if((match != -1)&&((getLabAge('eGFRdate')>12)||(getLabAge('Napldate')>12))){
		AddIfMissingTitle('LYTES1',"On Diuretic or ACE with last lytes over 12 months ago");
		hilite('LYTES1');
		document.getElementById('eGFRdate').style.cssText += ';'+cssString;
		document.getElementById('Napldate').style.cssText += ';'+cssString;
	}

	var FreeText = /METHOTREXATE/i;
	var match = allRx.search(FreeText);
	if((match != -1)&&(getLabAge('ALTdate')>1)){
		AddIfMissingTitle('LFT1', "On MTX with last bloodwork over a month ago");
		AddIfMissingTitle('CBC1', "On MTX with last bloodwork over a month ago");
		hilite('LFT1');
		hilite('CBC1');
		AddIfMissingStyle('ALTdate',cssString);
	}

	var FreeText = /DIGOXIN/i;
	var match = allRx.search(FreeText);
	if(match != -1){
	 	 AddIfMissingHTML('DRUG2', "<input type='checkbox' name='Dig1' id='Dig1'\
		 onClick=\"\
			add('subject','Digoxin ');\
			document.lab.DrugMonitoring.value='X';\
			add('DrugName1','Digoxin ');\
			document.lab.Sodium.value='X';\
			document.lab.Potassium.value='X';\
			document.lab.Creatinine.value='X';\
			add('Instructions',pi);\
			add('Instructions','Get the blood tested before taking the days Digoxin. ');\
		\">Digoxin<br>","Digoxin");
		if( (getLabAge('eGFRdate')>12)||(getLabAge('Kpldate')>12) ){
			AddIfMissingTitle('Dig1',"On Digoxin with last bloodwork over 12 months ago");
			hilite('DRUG1');
			AddIfMissingStyle('eGFRdate',cssString);
			AddIfMissingStyle('Kpldate',cssString);
		}
	}

	var FreeText = /LITHIUM/i;
	var match = allRx.search(FreeText);
	// logic : if pt is on meds and the last test is over a year ago
	if((match != -1)&&((getLabAge('eGFRdate')>12)||(getLabAge('Napldate')>12))){
		AddIfMissingHTML('DRUG2', "<input type='checkbox' name='Lithium1'\
		onClick=\"\
			add('subject','Lithium ');\
			document.lab.DrugMonitoring.value='X';\
			add('DrugName1','Lithium ';)\
			document.lab.Sodium.value='X';\
			document.lab.Potassium.value='X';\
			document.lab.Creatinine.value='X';\
			add('Instructions',pi);\
			add('Instructions','Get the blood tested before taking the days Lithium. ');\
		\">Lithium<br>","Lithium");
		hilite('DRUG1');
		AddIfMissingStyle('eGFRdate',cssString);
		AddIfMissingStyle('Kpldate',cssString);
	}

	var FreeText = /PHENYTOIN/i;
	var match = allRx.search(FreeText);
	// logic : if pt is on meds and the last test is over a year ago
	if((match != -1)&&(getLabAge('eGFRdate')>12)){
		AddIfMissingHTML('DRUG2', "<input type='checkbox' name='Phenytoin1'\
		onClick=\"\
			add('subject','Phenytoin ');\
			document.lab.DrugMonitoring.value='X';\
			add('DrugName1','Phenytoin ');\
			document.lab.Creatinine.value='X';\
			add('Instructions',pi);\
			add('Instructions','The timing of the Phenytoin bloodtest is not important. ');\
		\">Phenytoin<br>", "Phenytoin");
		hilite('DRUG1');
		AddIfMissingStyle('eGFRdate',cssString);
	}

	var FreeText = /VALPROIC/i;
	var match = allRx.search(FreeText);
	// logic : if pt is on meds and the last test is over a year ago
	if((match != -1)&&(getLabAge('ALTdate')>12)){
		AddIfMissingHTML('DRUG2', "<input type='checkbox' name='Valproate1'\
		onClick=\"\
			add('subject','Valproate ');\
			document.lab.DrugMonitoring.value='X';\
			add('DrugName2','Valproic ');\
			document.lab.ALT.value='X';\
			document.lab.CBC.value='X';\
			add('Instructions',pi);\
			add('Instructions','Have the blood taken before the Valproate dose. ');\
		\">Valproate<br>", "Valproate");
		hilite('DRUG1');
		AddIfMissingStyle('ALTdate',cssString);
	}

	var FreeText = /CARBAMAZEPINE/i;
	var string = document.getElementById("searchboxRx").value;
	var match = string.search(FreeText);
	// logic : if pt is on meds and the last test is over a year ago
	if((match != -1)&&(getLabAge('ALTdate')>12)){
		AddIfMissingHTML('DRUG2', "<input type='checkbox' name='Carbamazepine1'\
		onClick=\"\
			add('subject','Carbamazepine ');\
			document.lab.DrugMonitoring.value='X';\
			add('DrugName2','Carbamazepine ');\
			document.lab.Sodium.value='X';\
			document.lab.ALT.value='X';\
			document.lab.CBC.value='X';\
			add('Instructions',pi);\
			add('Instructions','Have the blood taken before the Carbamazepine dose. ');\
		\">Carbamazepine<br>","Carbamazepine");
		hilite('DRUG1');
		AddIfMissingStyle('ALTdate',cssString);
	}
	var FreeText = /THEOPHYLLINE/i;
	var match = allRx.search(FreeText);
	// logic : if pt is on meds
	if(match != -1){
		AddIfMissingHTML('DRUG2',"<input type='checkbox' name='Theophylline1'\
		onClick=\"\
			add('subject','Theophylline ');\
			document.lab.DrugMonitoring.value='X';\
			add('DrugName2','Theophylline ');\
			add('Instructions',pi);\
			add('Instructions','Have the blood taken before the Theophyline dose. ');\
		\">Theophyline<br>","Theophylline");
		//hilite('DRUG1');
	}


}









