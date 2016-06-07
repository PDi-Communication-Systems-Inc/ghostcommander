<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="xml" indent="yes" encoding="utf-8"/>
	<xsl:template match="resources">
		<xsl:copy>
		<xsl:variable name="r" select="."/>
		<xsl:variable name="dd" select="document('res/values/strings.xml')"/>
		<xsl:for-each select="$dd/resources/string">
			<xsl:variable name="lc" select="$r/string[@name=current()/@name]"/>
				<xsl:choose>
				   <xsl:when test="$lc">  <xsl:copy-of select="$lc"/></xsl:when>
				   <xsl:otherwise>  <xsl:copy-of select="."/><xsl:comment> !!! </xsl:comment></xsl:otherwise>
				</xsl:choose>
<xsl:text>
</xsl:text>
  		</xsl:for-each>
		</xsl:copy>
	</xsl:template>
</xsl:stylesheet>