README.txt  (C) 2011-2025 Peter Hutten-Czapski, MD

**********************************************************
* Thanks for downloading CARLOS EMR - Technical Demonstration *
**********************************************************

This version is fully functional but has limitations as listed,
If you mitigate those limits it may be suitable as is for a small clinic.

*or*

you can just type https://localhost:8443/carlos and figure it out later
(this file is stored at /usr/share/carlos-emr/README.txt)

Initial login credentials are generated at installation time.
Check the installation console output for your credentials.
They will be printed during the debian package installation.

LIMITATIONS
===========
This is a technical demonstration of CARLOS's features, but to ease
installation some short cuts were taken.

  1) Lab reports. While they can be manually uploaded as is, usually we
	configure an encrypted channel to automatically load them (push labs)
	into CARLOS as they come in.
  2) CARLOS Fax, Kiosk, MyCARLOS, Integrator and any other connected systems
	that give you additional functionality are optional and 
	separately configured.
  3) The installation scripts have made a few assumptions about who you are 
	and how you want to use CARLOS EMR.

CARLOS system configuration CAN be done by the enterprising user.  However
if you are a physician and you are intending to use CARLOS EMR
every day in your practice it is FAR more efficient/safe and, in the end,
cheaper, for you to hire a reputable CARLOS Service Provider (CSP) to configure
it for you and train you and your staff.
 
 BACKUP
 ======
 We have installed an encrypted backup to run at 2301h every day.  If you want
 to change that you need to sudo crontab -e

 DRUGREF
 =======
 We have installed drugref, our opensource drug database, with a current
 list of medications from Health Canada. When the list of meds starts feeling
 stale you can update from within CARLOS EMR through an Admin link

 ROURKE 
 ======
 While CARLOS EMR is open source software, some other components
 such as the Rourke Baby Form, when installed, 
 are included under licence from the copyright holder.
 
 MORE INFO
 =========
 Navigate to https://github.com/carlos-emr/carlos for help on using any of CARLOS's
 functions, and (albeit geeky) tips on how to tweak the setup.
