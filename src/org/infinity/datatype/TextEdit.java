// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.datatype;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.EnumMap;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;

import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rtextarea.RTextArea;
import org.infinity.gui.InfinityScrollPane;
import org.infinity.gui.InfinityTextArea;
import org.infinity.gui.StructViewer;
import org.infinity.gui.ViewerUtil;
import org.infinity.icon.Icons;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.StructEntry;
import org.infinity.util.io.FileWriterNI;

public final class TextEdit extends Datatype implements Editable, IsTextual
{
  public static enum EOLType {
    UNIX, WINDOWS
  }

  public static enum Align {
    LEFT, RIGHT, TOP, BOTTOM
  }

  private static final EnumMap<EOLType, String> EOL = new EnumMap<EOLType, String>(EOLType.class);
  static {
    EOL.put(EOLType.UNIX, "\n");
    EOL.put(EOLType.WINDOWS, "\r\n");
  }

  private InfinityTextArea textArea;
  private Align buttonAlign;
  private byte[] bytes;
  private String text;
  private EOLType eolType;
  private String charsetName;
  private boolean terminateString, editable;

  public TextEdit(byte buffer[], int offset, int length, String name)
  {
    this(null, buffer, offset, length, name, Align.RIGHT);
  }

  public TextEdit(byte buffer[], int offset, int length, String name, Align buttonAlignment)
  {
    this(null, buffer, offset, length, name, buttonAlignment);
  }

  public TextEdit(StructEntry parent, byte buffer[], int offset, int length, String name)
  {
    this(parent, buffer, offset, length, name, Align.RIGHT);
  }

  public TextEdit(StructEntry parent, byte buffer[], int offset, int length, String name, Align buttonAlignment)
  {
    super(parent, offset, length, name);
    read(buffer, offset);
    this.eolType = EOLType.UNIX;
    this.charsetName = Charset.defaultCharset().name();
    this.terminateString = false;
    this.editable = true;
    this.buttonAlign = (buttonAlignment != null) ? buttonAlignment : Align.RIGHT;
  }

  // --------------------- Begin Interface Editable ---------------------

  @Override
  public JComponent edit(ActionListener container)
  {
    JButton bUpdate;
    if (textArea == null) {
      textArea = new InfinityTextArea(1, 200, true);
      textArea.setHighlightCurrentLine(editable);
      textArea.setWrapStyleWord(true);
      textArea.setLineWrap(true);
      textArea.setMargin(new Insets(3, 3, 3, 3));
      textArea.setDocument(new FixedDocument(textArea, bytes.length));
      textArea.setEditable(editable);
    }
    textArea.setText(toString());
    textArea.setCaretPosition(0);
    textArea.discardAllEdits();
    InfinityScrollPane scroll = new InfinityScrollPane(textArea, true);
    scroll.setLineNumbersEnabled(false);

    bUpdate = new JButton("Update value", Icons.getIcon("Refresh16.gif"));
    bUpdate.setEnabled(editable);
    bUpdate.addActionListener(container);
    bUpdate.setActionCommand(StructViewer.UPDATE_VALUE);

    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    JPanel panel = new JPanel(gbl);

    int curGridX = 0;
    int curGridY = 0;
    if (buttonAlign == Align.TOP) {
      gbc = ViewerUtil.setGBC(gbc, curGridX, curGridY, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
                              GridBagConstraints.NONE, new Insets(4, 0, 6, 0), 0, 0);
      panel.add(bUpdate, gbc);
      curGridY++;
    }
    if (buttonAlign == Align.LEFT) {
      gbc = ViewerUtil.setGBC(gbc, curGridX, curGridY, 1, 1, 0.0, 1.0, GridBagConstraints.CENTER,
                              GridBagConstraints.NONE, new Insets(0, 0, 0, 6), 0, 0);
      panel.add(bUpdate, gbc);
      curGridX++;
    }

    gbc = ViewerUtil.setGBC(gbc, curGridX, curGridY, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER,
                            GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);
    panel.add(scroll, gbc);

    if (buttonAlign == Align.RIGHT) {
      gbc = ViewerUtil.setGBC(gbc, curGridX + 1, curGridY, 1, 1, 0.0, 1.0, GridBagConstraints.CENTER,
                              GridBagConstraints.NONE, new Insets(0, 6, 0, 0), 0, 0);
      panel.add(bUpdate, gbc);
    }

    if (buttonAlign == Align.BOTTOM) {
      gbc = ViewerUtil.setGBC(gbc, curGridX, curGridY + 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
                              GridBagConstraints.NONE, new Insets(6, 0, 4, 0), 0, 0);
      panel.add(bUpdate, gbc);
    }

    panel.setMinimumSize(DIM_BROAD);
    panel.setPreferredSize(DIM_BROAD);
    return panel;
  }

  @Override
  public void select()
  {
  }

  @Override
  public boolean updateValue(AbstractStruct struct)
  {
    text = textArea.getText();

    // notifying listeners
    fireValueUpdated(new UpdateEvent(this, struct));

    return true;
  }

  // --------------------- End Interface Editable ---------------------


  // --------------------- Begin Interface Writeable ---------------------

  @Override
  public void write(OutputStream os) throws IOException
  {
    FileWriterNI.writeBytes(os, toArray());
  }

  // --------------------- End Interface Writeable ---------------------

//--------------------- Begin Interface Readable ---------------------

  @Override
  public int read(byte[] buffer, int offset)
  {
    bytes = Arrays.copyOfRange(buffer, offset, offset + getSize());

    return offset + getSize();
  }

//--------------------- End Interface Readable ---------------------

//--------------------- Begin Interface IsTextual ---------------------

  @Override
  public String getText()
  {
    if (text == null) {
      try {
        int len = 0;
        while (len < bytes.length && bytes[len] != 0) {
          len++;
        }
        text = eolConvert(new String(bytes, 0, len, charsetName), System.getProperty("line.separator"));
      } catch (UnsupportedEncodingException e) {
        text = eolConvert(new String(bytes, 0, bytes.length), System.getProperty("line.separator"));
        e.printStackTrace();
      }
    }
    return text;
  }

//--------------------- End Interface IsTextual ---------------------

  @Override
  public String toString()
  {
    return getText();
  }

  public byte[] toArray()
  {
    if (text != null) {
      byte[] buf = null;
      try {
        buf = eolConvert(text).getBytes(charsetName);
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
        buf = eolConvert(text).getBytes();
      }
      if (buf != null) {
        // XXX: multibyte encodings may cause issues
        int imax = buf.length < bytes.length ? buf.length : bytes.length;
        for (int i = 0; i < imax; i++)
          bytes[i] = buf[i];
        for (int i = imax; i < bytes.length; i++)
          bytes[i] = 0;
        if (terminateString)
          bytes[bytes.length - 1] = 0;    // ensure null-termination
      }
    }
    return bytes;
  }

  public EOLType getEolType()
  {
    return eolType;
  }

  public void setEolType(EOLType type)
  {
    if (type != null)
      eolType = type;
  }

  public boolean getStringTerminated()
  {
    return terminateString;
  }

  public void setStringTerminated(boolean terminated)
  {
    terminateString = terminated;
  }

  public String getCharset()
  {
    return charsetName;
  }

  public boolean setCharset(String charsetName)
  {
    if (Charset.isSupported(charsetName)) {
      this.charsetName = charsetName;
      return true;
    } else {
      return false;
    }
  }

  public boolean getEditable()
  {
    return editable;
  }

  public void setEditable(boolean edit)
  {
    editable = edit;
  }

  private String eolConvert(String s)
  {
    if (s != null && s.length() > 0)
      return s.replaceAll("(\r\n|\n)", EOL.get(eolType));
    else
      return s;
  }

  private String eolConvert(String s, String eol)
  {
    if (s != null && s.length() > 0 && eol != null && eol.length() > 0)
      return s.replaceAll("(\r\n|\n)", eol);
    else
      return s;
  }


//-------------------------- INNER CLASSES --------------------------

  // Ensures a size limit on byte level
  private class FixedDocument extends RSyntaxDocument
  {
    private int maxLength;
    private RTextArea textArea;

    FixedDocument(RTextArea text, int length)
    {
      super(null);
      textArea = text;
      maxLength = length >= 0 ? length : 0;
    }

    @Override
    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException
    {
      if (str == null || textArea == null ||
          eolConvert(textArea.getText()).getBytes().length + eolConvert(str).getBytes().length > maxLength)
        return;
      super.insertString(offs, str, a);
    }
  }
}