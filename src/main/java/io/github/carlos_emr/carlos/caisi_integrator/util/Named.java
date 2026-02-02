package io.github.carlos_emr.carlos.caisi_integrator.util;

import java.util.Comparator;

/**
 * The intent of this interface is so that any time I have
 * a daos model object that has a "name" (or any object that has a name),
 * I can use the comparator below to sort it before display it on the screen.
 * 
 * For the most part it's just so I don't have to write a slew of comparators
 * like a comparator for Facility.name, and SiteUser.name, and Provider.name etc.
 */
public interface Named
{
	public static final Comparator<Named> NAME_COMPARATOR=new Comparator<Named>()
	{
		@Override
		public int compare(Named o1, Named o2)
		{
			return(o1.getName().compareTo(o2.getName()));
		}
	};

	
	public String getName();
}
