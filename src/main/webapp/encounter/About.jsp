<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@page import="io.github.carlos_emr.CarlosProperties" %>


<html>
    <head>
        <title>About CARLOS EMR</title>

        <style type="text/css">
            p.build_info {
                padding-left: 36px;
            }

            h1 {
                padding-left: 36px;
            }

            p {
                color: #666666;
                font-family: verdana;
                font-size: 10px;
            }

            p.close {
                color: #666666;
                font-family: verdana;
                font-size: 16px;
            }

            A:link {
                color: #666666;
            }

            A:visited {
                color: #666666;
            }

            A:active {
                color: #666666;
            }

            A:hover {
                color: black;
            }

        </style>

    </head>

    <body bgcolor="#B7B18D">

    <table width="600" cellspacing="0" cellpadding="0" align="center">

        <!--instead of using css for the border I am using an image so the look is seamless between the table and openosp-logo.png image-->
        <td background="<%= request.getContextPath() %>/images/about_layout/table_body_bkg.jpg">

            <!--START about CARLOS body table-->
            <table width="560" align="center" cellspacing="0" cellpadding="0">
                <td>
                    <h1>About CARLOS EMR</h1>

                    <p class="build_info">build date: <%= CarlosProperties.getBuildDate() %><br/>
                        build tag: <%=CarlosProperties.getBuildTag()%>
                    </p>

                    <table width="85%" align="center">
                        <td>
                            <p><u>About Us</u></p>

                            <p>CARLOS EMR is an open-source electronic medical record system for Canadian
                                healthcare, collaboratively developed by a community of healthcare providers
                                and developers.</p>

                            <br/>

                            <p><u>Terms</u></p>

                            <p>
                                Copyright (c) 2001-2015. Department of Family
                                Medicine, McMaster University. All Rights Reserved. This software is
                                published under the GPL GNU General Public License. This program is
                                free software; you can redistribute it and/or modify it under the
                                terms of the GNU General Public License as published by the Free
                                Software Foundation; either version 2 of the License, or (at your
                                option) any later version. This program is distributed in the hope
                                that it will be useful, but WITHOUT ANY WARRANTY; without even the
                                implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
                                PURPOSE. See the GNU General Public License for more details. You
                                should have received a copy of the GNU General Public License along
                                with this program; if not, write to the Free Software Foundation,
                                Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA. <br>
                                <br>
                                This software was written for the<br>
                                Department of Family Medicine<br>
                                McMaster University<br>
                                Hamilton<br>
                                Ontario, Canada <br>
                                <br>
                            </p>

                            <p align="right"><a href="javascript: self.close()"> Close| </a></p>

                        </td>
                    </table>

                    <img src="<%= request.getContextPath() %>/images/about_layout/table_bottom_green.jpg" border="0" align="ABSBOTTOM">

                </td>
            </table>
            <!--END about CARLOS body table-->

        </td>
        </tr>

    </table>

    </body>

</html>
