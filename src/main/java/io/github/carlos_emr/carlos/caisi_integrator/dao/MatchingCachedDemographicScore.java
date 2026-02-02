package io.github.carlos_emr.carlos.caisi_integrator.dao;

public class MatchingCachedDemographicScore implements Comparable<MatchingCachedDemographicScore>
{
    public CachedDemographic cachedDemographic;
    public int score;
    
    public MatchingCachedDemographicScore() {
        this.cachedDemographic = null;
        this.score = 0;
    }
    
    @Override
    public int compareTo(final MatchingCachedDemographicScore o) {
        return this.score - o.score;
    }
}
