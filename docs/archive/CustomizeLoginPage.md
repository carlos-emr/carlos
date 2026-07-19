# How to Customize the Login Page

Instructions on how to customize Oscar's new login page with clinic and support provider information. 

## New Design

- The new login page design now securely displays custom resource information such as logo images and text. 

- HTML is no longer rendered and images much be place in a specific directory in Oscar Documents.

- Plan text only.

- All files and directories must be named exactly as described in this document. 

## Resource Directory

All the custom resources must be placed inside a directory named "login" and then placed inside the root of OscarDocument

```
/var/lib/OscarDocument/login
```

The login directory must be accessible to the Tomcat user. 

## Text Data

** Plain Text only. HTML will not be rendered. **

All of the text data is placed in a key-value properties file named ".env" located in the root of the login directory. A file example is included at the end of this document.

```
/var/lib/OscarDocument/login/.env
```

### *tabName*
Text is placed in the tab bar of the browser window. Default value is "Oscar"

```
tabName = My Clinic
```

### *supportLink*
Creates a link to external resources for the support provider. ** No HTML required **.

```
supportLink = https://support.ticket.system/
```

### *supportName*
The name of the support provider.  This text can be left blank if a logo is being used. 

```
supportName = Champion OSP
```

### *supportText*
Sub text for the support provider such as the address, phone, email, url etc...

```
supportText = email@champion.com \n 607.345.2132
```

### *clinicLink*
Creates a link to an external resource for the clinic. ** No HTML required **.

```
clinicLink = https://family.healthcare.com/
```

### *clinicName*
Name or title of the clinic.

```
clinicName = Supreme Family Health
```

### *clinicText*
Sub text for the clinic such as address, phone, email url etc.

```
clinicText = phone: 455.232.2344 \n fax: 233.234.5466
```

## Images
Images must be placed in the root of the login directory. The image names must be exactly as described.

### *clinicLogo.png*

```
/var/lib/OscarDocument/login/clinicLogo.png
```
- This image is placed directly below the login dialog. 
- It must be formated as a PNG.
- Maximum width of the image is 500px. 
- For best results, recommended width is 500px or greater. 

### *supportLogo.png*

```
/var/lib/OscarDocument/login/supportLogo.png
```
- This image is placed at the very top at the login page above the login dialog. 
- It must be formated as a PNG.
- Maximum width of the image is 150px. 
- For best results, recommended width is 150px or greater.

## Acceptable Use Agreement
The Acceptable Use Agreement should be saved as a text file inside the root of the login directory. Use of an AUA is a support provider preference. 

Enable the AUA with the show_aua key in the oscar_mcmaster.properties file. 

```
/var/lib/OscarDocument/login/AcceptableUseAgreement.txt
```

## Login Page Diagram

![alt text](login_page.png)

## Example .env File

```
## All settings are optional

## Support data is placed under the Login dialog

### HTML link to the support provider website.

supportLink=

### Sub text for the support provider. such as telephone or email.

supportText=

### Full name or title of the support provider.

supportName=

## Clinic information is placed above the login dialog under
## a clinic logo if one is supplied.

### clinic subtext such as address phone etc.

clinicText=

### clinic HTML link to a clinic website if one is supplied.

clinicLink=

### Name or title of the clinic

clinicName=

## Name or title that goes in the browser window tab
## This is set to Oscar by default

tabName=
```