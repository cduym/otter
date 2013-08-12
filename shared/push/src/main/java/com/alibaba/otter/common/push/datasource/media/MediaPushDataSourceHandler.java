package com.alibaba.otter.common.push.datasource.media;

import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.otter.common.push.datasource.DataSourceHanlder;
import com.alibaba.otter.shared.common.model.config.data.DataMediaType;
import com.alibaba.otter.shared.common.model.config.data.db.DbMediaSource;
import com.google.common.base.Function;
import com.google.common.collect.GenericMapMaker;
import com.google.common.collect.MapEvictionListener;
import com.google.common.collect.MapMaker;

/**
 * media group 的 url 为： jdbc:mysql://groupKey=xxx
 * 
 * @author jianghang 2013-4-18 下午03:33:28
 * @version 4.1.8
 */
public class MediaPushDataSourceHandler implements DataSourceHanlder {

    private static final Logger                       log     = LoggerFactory.getLogger(MediaPushDataSourceHandler.class);

    private static final Pattern                      PATTERN = Pattern.compile("jdbc:mysql://groupKey=([^&/]+).*",
                                                                  Pattern.CASE_INSENSITIVE);

    /**
     * 一个pipeline下面有一组DataSource.<br>
     * key = pipelineId<br>
     * value = key(dataMediaSourceId)-value(DataSource)<br>
     */
    private Map<Long, Map<DbMediaSource, DataSource>> dataSources;

    public MediaPushDataSourceHandler(){
        // 设置soft策略
        GenericMapMaker mapMaker = new MapMaker().softValues();
        mapMaker = ((MapMaker) mapMaker).evictionListener(new MapEvictionListener<Long, Map<DbMediaSource, DataSource>>() {

            public void onEviction(Long pipelineId, Map<DbMediaSource, DataSource> dataSources) {
                if (dataSources == null) {
                    return;
                }

                for (DataSource dataSource : dataSources.values()) {
                    try {
                        MediaPushDataSource mediaPushDataSource = (MediaPushDataSource) dataSource;
                        mediaPushDataSource.destory();
                    } catch (SQLException e) {
                        log.error("ERROR ## close the datasource has an error", e);
                    }
                }
            }
        });

        // 构建第一层map
        dataSources = new MapMaker().makeComputingMap(new Function<Long, Map<DbMediaSource, DataSource>>() {

            public Map<DbMediaSource, DataSource> apply(Long pipelineId) {
                // 构建第二层map
                return new MapMaker().makeComputingMap(new Function<DbMediaSource, DataSource>() {

                    public DataSource apply(DbMediaSource dbMediaSource) {
                        return createDataSource(dbMediaSource.getUrl(),
                            dbMediaSource.getUsername(),
                            dbMediaSource.getPassword(),
                            dbMediaSource.getDriver(),
                            dbMediaSource.getType(),
                            dbMediaSource.getEncode());
                    }

                });
            }
        });
    }

    public boolean support(DbMediaSource dbMediaSource) {
        return isMediaPushDataSource(dbMediaSource.getUrl());
    }

    public boolean support(DataSource dataSource) {
        if (dataSource == null) {
            return false;
        }
        return dataSource instanceof MediaPushDataSource;
    }

    public DataSource create(Long pipelineId, DbMediaSource dbMediaSource) {
        return dataSources.get(pipelineId).get(dbMediaSource);
    }

    protected DataSource createDataSource(String url, String userName, String password, String driverClassName,
                                          DataMediaType dataMediaType, String encoding) {
        MediaInfo media = parseMediaInfo(url);
        if (media == null) {
            if (isMediaPushDataSource(url)) {
                log.error("{} can't parse as an media groupdatasource, please check!", url);
            } else {
                log.info("{} is not a media datasource", url);
            }
            return null;
        }

        String groupKey = media.getGroupKey();
        MediaPushDataSource mediaDataSource = new MediaPushDataSource(url,
            userName,
            password,
            driverClassName,
            dataMediaType,
            encoding);
        mediaDataSource.setDbGroupKey(groupKey);
        mediaDataSource.init();
        return mediaDataSource;
    }

    @Override
    public boolean destory(Long pipelineId) {
        Map<DbMediaSource, DataSource> sources = dataSources.remove(pipelineId);
        if (sources != null) {
            for (DataSource dataSource : sources.values()) {
                try {
                    MediaPushDataSource mediaPushDataSource = (MediaPushDataSource) dataSource;
                    mediaPushDataSource.destory();
                } catch (SQLException e) {
                    log.error("ERROR ## close the datasource has an error", e);
                }
            }

            sources.clear();
        }

        return true;
    }

    public static boolean isMediaPushDataSource(String url) {
        return StringUtils.startsWithIgnoreCase(url, "jdbc:") && StringUtils.containsIgnoreCase(url, "groupKey");
    }

    // 解析 url
    public static MediaInfo parseMediaInfo(String url) {
        if (StringUtils.isEmpty(url)) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(url.trim());
        if (!matcher.matches()) {
            return null;
        }

        if (matcher.groupCount() < 1) {
            throw new IllegalArgumentException(url
                                               + " is a media push datasource but have no enough info for groupKey.");
        }
        return new MediaInfo(matcher.group(1));
    }

    public static class MediaInfo {

        String groupKey;

        public MediaInfo(String groupKey){
            this.groupKey = groupKey;
        }

        public String getGroupKey() {
            return groupKey;
        }

    }

}