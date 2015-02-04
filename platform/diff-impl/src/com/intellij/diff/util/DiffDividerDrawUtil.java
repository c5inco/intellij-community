/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.util;

import com.intellij.openapi.diff.impl.splitter.Transformation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DiffDividerDrawUtil {
  public static void paintSeparators(@NotNull Graphics2D gg,
                                     int width,
                                     @NotNull Editor editor1,
                                     @NotNull Editor editor2,
                                     @NotNull DividerSeparatorPaintable paintable) {
    List<DividerSeparator> polygons = createVisibleSeparators(editor1, editor2, paintable);

    GraphicsConfig config = GraphicsUtil.setupAAPainting(gg);
    for (DividerSeparator polygon : polygons) {
      polygon.paint(gg, width);
    }
    config.restore();
  }

  public static void paintSeparatorsOnScrollbar(@NotNull Graphics2D gg,
                                                int width,
                                                @NotNull Editor editor1,
                                                @NotNull Editor editor2,
                                                @NotNull DividerSeparatorPaintable paintable) {
    List<DividerSeparator> polygons = createVisibleSeparators(editor1, editor2, paintable);

    GraphicsConfig config = GraphicsUtil.setupAAPainting(gg);
    for (DividerSeparator polygon : polygons) {
      polygon.paintOnScrollbar(gg, width);
    }
    config.restore();
  }

  public static void paintPolygons(@NotNull Graphics2D gg,
                                   int width,
                                   @NotNull Editor editor1,
                                   @NotNull Editor editor2,
                                   @NotNull DividerPaintable paintable) {
    List<DividerPolygon> polygons = createVisiblePolygons(editor1, editor2, paintable);

    GraphicsConfig config = GraphicsUtil.setupAAPainting(gg);
    for (DividerPolygon polygon : polygons) {
      polygon.paint(gg, width);
    }
    config.restore();
  }

  public static void paintSimplePolygons(@NotNull Graphics2D gg,
                                         int width,
                                         @NotNull Editor editor1,
                                         @NotNull Editor editor2,
                                         @NotNull DividerPaintable paintable) {
    List<DividerPolygon> polygons = createVisiblePolygons(editor1, editor2, paintable);

    GraphicsConfig config = GraphicsUtil.setupAAPainting(gg);
    for (DividerPolygon polygon : polygons) {
      polygon.paintSimple(gg, width);
    }
    config.restore();
  }

  public static void paintPolygonsOnScrollbar(@NotNull Graphics2D g,
                                              int width,
                                              @NotNull Editor editor1,
                                              @NotNull Editor editor2,
                                              @NotNull DividerPaintable paintable) {
    List<DividerPolygon> polygons = createVisiblePolygons(editor1, editor2, paintable);

    for (DividerPolygon polygon : polygons) {
      polygon.paintOnScrollbar(g, width);
    }
  }

  @NotNull
  public static List<DividerPolygon> createVisiblePolygons(@NotNull Editor editor1,
                                                           @NotNull Editor editor2,
                                                           @NotNull DividerPaintable paintable) {
    final List<DividerPolygon> polygons = new ArrayList<DividerPolygon>();

    final Transformation[] transformations = new Transformation[]{getTransformation(editor1), getTransformation(editor2)};

    final Interval leftInterval = getVisibleInterval(editor1);
    final Interval rightInterval = getVisibleInterval(editor2);

    paintable.process(new DividerPaintable.Handler() {
      @Override
      public boolean process(int startLine1, int endLine1, int startLine2, int endLine2, @NotNull Color color) {
        if (leftInterval.startLine > endLine1 && rightInterval.startLine > endLine2) return true;
        if (leftInterval.endLine < startLine1 && rightInterval.endLine < startLine2) return false;

        polygons.add(createPolygon(transformations, startLine1, endLine1, startLine2, endLine2, color));
        return true;
      }
    });

    return polygons;
  }

  @NotNull
  public static List<DividerSeparator> createVisibleSeparators(@NotNull Editor editor1,
                                                               @NotNull Editor editor2,
                                                               @NotNull DividerSeparatorPaintable paintable) {
    final List<DividerSeparator> separators = new ArrayList<DividerSeparator>();

    final Transformation[] transformations = new Transformation[]{getTransformation(editor1), getTransformation(editor2)};

    final Interval leftInterval = getVisibleInterval(editor1);
    final Interval rightInterval = getVisibleInterval(editor2);

    final int height1 = editor1.getLineHeight();
    final int height2 = editor2.getLineHeight();

    paintable.process(new DividerSeparatorPaintable.Handler() {
      @Override
      public boolean process(int line1, int line2) {
        if (leftInterval.startLine > line1 + 1 && rightInterval.startLine > line2 + 1) return true;
        if (leftInterval.endLine < line1 && rightInterval.endLine < line2) return false;

        separators.add(createSeparator(transformations, line1, line2, height1, height2));
        return true;
      }
    });

    return separators;
  }

  @NotNull
  private static Transformation getTransformation(@NotNull final Editor editor) {
    return new Transformation() {
      @Override
      public int transform(int line) {
        int yOffset = editor.logicalPositionToXY(new LogicalPosition(line, 0)).y;

        final JComponent header = editor.getHeaderComponent();
        int headerOffset = header == null ? 0 : header.getHeight();

        return yOffset - editor.getScrollingModel().getVerticalScrollOffset() + headerOffset;
      }
    };
  }

  @NotNull
  private static DividerPolygon createPolygon(@NotNull Transformation[] transformations,
                                              int startLine1, int endLine1,
                                              int startLine2, int endLine2,
                                              @NotNull Color color) {
    int start1 = transformations[0].transform(startLine1);
    int end1 = transformations[0].transform(endLine1);
    int start2 = transformations[1].transform(startLine2);
    int end2 = transformations[1].transform(endLine2);
    return new DividerPolygon(start1, start2, end1, end2, color);
  }

  @NotNull
  private static DividerSeparator createSeparator(@NotNull Transformation[] transformations,
                                                  int line1, int line2, int height1, int height2) {
    int start1 = transformations[0].transform(line1);
    int start2 = transformations[1].transform(line2);
    return new DividerSeparator(start1, start2, start1 + height1, start2 + height2);
  }

  @NotNull
  private static Interval getVisibleInterval(Editor editor) {
    Rectangle area = editor.getScrollingModel().getVisibleArea();
    LogicalPosition position1 = editor.xyToLogicalPosition(new Point(0, area.y));
    LogicalPosition position2 = editor.xyToLogicalPosition(new Point(0, area.y + area.height));
    return new Interval(position1.line, position2.line);
  }

  public interface DividerPaintable {
    void process(@NotNull Handler handler);

    interface Handler {
      boolean process(int startLine1, int endLine1, int startLine2, int endLine2, @NotNull Color color);
    }
  }

  public interface DividerSeparatorPaintable {
    void process(@NotNull Handler handler);

    interface Handler {
      boolean process(int line1, int line2);
    }
  }

  /**
   * A polygon, which is drawn between editors in merge or diff dialogs, and which indicates the change flow from one editor to another.
   */
  public static class DividerPolygon {
    // pixels from the top of editor
    private final int myStart1;
    private final int myStart2;
    private final int myEnd1;
    private final int myEnd2;
    @NotNull private final Color myColor;

    public DividerPolygon(int start1, int start2, int end1, int end2, @NotNull Color color) {
      myStart1 = start1;
      myStart2 = start2;
      myEnd1 = end1;
      myEnd2 = end2;
      myColor = color;
    }

    private void paint(Graphics2D g, int width) {
      // we need this shift, because editor background highlight is painted in range "Y(line) - 1 .. Y(line + 1) - 1"
      DiffDrawUtil.drawCurveTrapezium(g, 0, width, myStart1 - 1, myEnd1 - 1, myStart2 - 1, myEnd2 - 1, myColor);
    }

    private void paintSimple(Graphics2D g, int width) {
      DiffDrawUtil.drawTrapezium(g, 0, width, myStart1 - 1, myEnd1 - 1, myStart2 - 1, myEnd2 - 1, myColor);
    }

    private void paintOnScrollbar(Graphics2D g, int width) {
      int startY = myStart1 - 1;
      int endY = myEnd1 - 1;
      int height = endY - startY;

      int startX = 0;
      int endX = startX + width - 1;

      g.setColor(myColor);
      if (height > 2) {
        g.fillRect(startX, startY, width, height);

        Color framingColor = DiffDrawUtil.getFramingColor(myColor);
        UIUtil.drawLine(g, startX, startY, endX, startY, null, framingColor);
        UIUtil.drawLine(g, startX, endY, endX, endY, null, framingColor);
      }
      else {
        DiffDrawUtil.drawDoubleShadowedLine(g, startX, endX, startY, myColor);
      }
    }

    public String toString() {
      return "<" + myStart1 + ", " + myEnd1 + " : " + myStart2 + ", " + myEnd2 + "> " + myColor;
    }
  }

  public static class DividerSeparator {
    // pixels from the top of editor
    private final int myStart1;
    private final int myStart2;
    private final int myEnd1;
    private final int myEnd2;

    public DividerSeparator(int start1, int start2, int end1, int end2) {
      myStart1 = start1;
      myStart2 = start2;
      myEnd1 = end1;
      myEnd2 = end2;
    }

    private void paint(Graphics2D g, int width) {
      DiffDrawUtil.drawConnectorLineSeparator(g, 0, width, myStart1, myEnd1, myStart2, myEnd2);
    }

    private void paintOnScrollbar(Graphics2D g, int width) {
      DiffDrawUtil.drawConnectorLineSeparator(g, 0, width, myStart1, myEnd1, myStart1, myEnd1);
    }

    public String toString() {
      return "<" + myStart1 + ", " + myEnd1 + " : " + myStart2 + ", " + myEnd2 + "> ";
    }
  }

  private static class Interval {
    public final int startLine;
    public final int endLine;

    public Interval(int startLine, int endLine) {
      this.startLine = startLine;
      this.endLine = endLine;
    }
  }
}
