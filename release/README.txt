README.txt  (C) 2011-2025 Peter Hutten-Czapski, MD

**********************************************************
* Thanks for downloading OSCAR - Technical Demonstration *
**********************************************************

This version is fully functional but has limitations as listed,
If you mitigate those limits it may be suitable as is for a small clinic.

*or*

you can just type https://localhost:8443/oscar and figure it out later
(this file is stored at /usr/share/oscar-emr/README.txt)

Initial login credentials are
user:oscardoc
pass:mac2002
pin:1117

LIMITATIONS
===========
This is a technical demonstration of OSCAR's features, but to ease
installation some short cuts were taken.

  1) Lab reports. While they can be manually uploaded as is, usually we
	configure an encrypted channel to automatically load them (push labs)
	into OSCAR as they come in.
  2) OSCAR Fax, Kiosk, MyOSCAR, Integrator and any other connected systems
	that give you additional functionality are optional and 
	separately configured.
  3) The installation scripts have made a few assumptions about who you are 
	and how you want to use OSCAR.

OSCAR system configuration CAN be done by the enterprising user.  However 
if you are a physician and you are intending to use OSCAR EMR
every day in your practice it is FAR more efficient/safe and, in the end,
cheaper, for you to hire a reputable Oscar Service Provider (OSP) to configure
it for you and train you and your staff.
 
 BACKUP
 ======
 We have installed an encrypted backup to run at 2301h every day.  If you want
 to change that you need to sudo crontab -e

 DRUGREF
 =======
 We have installed drugref, our opensource drug database, with a current
 list of medications from Health Canada. When the list of meds starts feeling
 stale you can update from within OSCAR through an Admin link

 ROURKE 
 ======
 While OSCAR is open source software, some other components
 such as the Rourke Baby Form, when installed, 
 are included under licence from the copyright holder.
 
 MORE INFO
 =========
 Navigate to the www.oscargalaxy.org site for help on using any of OSCAR's 
 functions, and (albeit geeky) tips on how to tweak the setup.
