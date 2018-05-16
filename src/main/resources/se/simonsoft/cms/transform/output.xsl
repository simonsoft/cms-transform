<?xml version="1.0" encoding="UTF-8"?>
<!--

    Copyright (C) 2009-2017 Simonsoft Nordic AB

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

            http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:fn="http://www.w3.org/2005/xpath-functions"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	xmlns:xi="http://www.w3.org/2001/XInclude"
	xmlns:cms="http://www.simonsoft.se/namespace/cms"
	xmlns:cmsfn="http://www.simonsoft.se/namespace/cms-functions"
	version="2.0">
	
	<!-- Caller can request omitting the declaration, e.g. when storing in a property. -->
	<xsl:param name="omit-xml-declaration" select="'no'"/>
	
	<xsl:template match="@*|node()" mode="#all">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()" mode="#current"/>
		</xsl:copy>
	</xsl:template>
	
	
	<!-- Match the document root element. -->
	<xsl:template match="/element()"  priority="10">
		<xsl:variable name="doctype.public" as="xs:string?" select="/*/@cms:doctype-public"/>
		<xsl:variable name="doctype.system" as="xs:string?">
			<xsl:if test="boolean($doctype.public)">
				<!-- DOCTYPE declaration will be set ONLY is both public and system IDs are present. -->
				<!-- Schema with xsi delaration will have empty PublicId and the schema location in SystemId (will propagate as an attribute) -->
				<xsl:value-of select="/*/@cms:doctype-system"/>
			</xsl:if>
		</xsl:variable>
		
		<!-- Using 'xml' output method will preserve minimized empty tags from input. Actually, 'xml' will minimize all tags that happen to be empty. -->
		<xsl:result-document method="xml" doctype-public="{$doctype.public}" doctype-system="{$doctype.system}" omit-xml-declaration="{$omit-xml-declaration}">
			<!-- A DOCTYPE declaration will be output by Saxon if a doctype-system is present. -->
			<xsl:apply-templates select="." mode="output"/>
		</xsl:result-document>
	</xsl:template>
	
	<xsl:template match="@cms:doctype-public | @cms:doctype-system" mode="output">
		<!-- A DOCTYPE declaration can be produced for multiple output transforms by setting both @cms:doctype-public and @cms:doctype-system attributes. -->
		<!-- The primary output can be controlled by the standard result-document parameters. -->
	</xsl:template>
	
</xsl:stylesheet>
