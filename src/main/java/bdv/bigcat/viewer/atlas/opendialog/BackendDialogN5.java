package bdv.bigcat.viewer.atlas.opendialog;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import com.google.gson.JsonElement;

import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import javafx.stage.DirectoryChooser;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.type.NativeType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class BackendDialogN5 extends BackendDialogGroupAndDataset implements CombinesErrorMessages
{

	private static final String RESOLUTION_KEY = "resolution";

	private static final String OFFSET_KEY = "offset";

	private static final String MIN_KEY = "min";

	private static final String MAX_KEY = "max";

	private static final String AXIS_ORDER_KEY = "axisOrder";

	public BackendDialogN5()
	{
		super( "N5 group", "Dataset", ( group, scene ) -> {
			final DirectoryChooser directoryChooser = new DirectoryChooser();
			final File initDir = new File( group );
			directoryChooser.setInitialDirectory( initDir.exists() && initDir.isDirectory() ? initDir : new File( System.getProperty( "user.home" ) ) );
			final File directory = directoryChooser.showDialog( scene.getWindow() );
			return directory == null ? null : directory.getAbsolutePath();
		} );
	}

	private static boolean isLabelType( final DataType type )
	{
		return isLabelMultisetType( type ) || isIntegerType( type );
	}

	private static boolean isLabelMultisetType( final DataType type )
	{
		return false;
	}

	private static boolean isIntegerType( final DataType type )
	{
		switch ( type )
		{
		case INT8:
		case INT16:
		case INT32:
		case INT64:
		case UINT8:
		case UINT16:
		case UINT32:
		case UINT64:
			return true;
		default:
			return false;
		}
	}

	private static double minForType( final DataType t )
	{
		// TODO ever return non-zero here?
		switch ( t )
		{
		default:
			return 0.0;
		}
	}

	private static double maxForType( final DataType t )
	{
		switch ( t )
		{
		case UINT8:
			return 0xff;
		case UINT16:
			return 0xffff;
		case UINT32:
			return 0xffffffffl;
		case UINT64:
			return 2.0 * Long.MAX_VALUE;
		case INT8:
			return Byte.MAX_VALUE;
		case INT16:
			return Short.MAX_VALUE;
		case INT32:
			return Integer.MAX_VALUE;
		case INT64:
			return Long.MAX_VALUE;
		case FLOAT32:
		case FLOAT64:
			return 1.0;
		default:
			return 1.0;
		}
	}

	@Override
	public < T extends NativeType< T >, V extends Volatile< T > > Pair< RandomAccessibleInterval< T >, RandomAccessibleInterval< V > > getDataAndVolatile(
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final String group = groupProperty.get();
		final N5FSReader reader = new N5FSReader( group );
		final String dataset = this.dataset.get();
		final RandomAccessibleInterval< T > raw = N5Utils.openVolatile( reader, dataset );
		final RandomAccessibleInterval< V > vraw = VolatileViews.wrapAsVolatile( raw, sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, priority, true ) );
		return new ValuePair<>( raw, vraw );
	}

	@Override
	public boolean isLabelType() throws IOException
	{
		return isLabelType( new N5FSReader( groupProperty.get() ).getDatasetAttributes( dataset.get() ).getDataType() );
	}

	@Override
	public boolean isLabelMultisetType() throws IOException
	{
		return isLabelMultisetType( new N5FSReader( groupProperty.get() ).getDatasetAttributes( dataset.get() ).getDataType() );
	}

	@Override
	public boolean isIntegerType() throws IOException
	{
		return isIntegerType( new N5FSReader( groupProperty.get() ).getDatasetAttributes( dataset.get() ).getDataType() );
	}

	@Override
	public void updateDatasetInfo( final String dataset, final DatasetInfo info )
	{
		try
		{
			final N5FSReader reader = new N5FSReader( groupProperty.get() );

			final DatasetAttributes dsAttrs = reader.getDatasetAttributes( dataset );
			final int nDim = dsAttrs.getNumDimensions();

			final HashMap< String, JsonElement > attributes = reader.getAttributes( dataset );

			if ( attributes.containsKey( AXIS_ORDER_KEY ) )
			{
				final AxisOrder ao = reader.getAttribute( dataset, AXIS_ORDER_KEY, AxisOrder.class );
				datasetInfo.defaultAxisOrderProperty().set( ao );
				datasetInfo.selectedAxisOrderProperty().set( ao );
			}
			else
			{
				final Optional< AxisOrder > ao = AxisOrder.defaultOrder( nDim );
				if ( ao.isPresent() )
					this.datasetInfo.defaultAxisOrderProperty().set( ao.get() );
				if ( this.datasetInfo.selectedAxisOrderProperty().isNull().get() || this.datasetInfo.selectedAxisOrderProperty().get().numDimensions() != nDim )
					this.axisOrder().set( ao.get() );
			}

			this.datasetInfo.setResolution( Optional.ofNullable( reader.getAttribute( dataset, RESOLUTION_KEY, double[].class ) ).orElse( DoubleStream.generate( () -> 1.0 ).limit( nDim ).toArray() ) );
			this.datasetInfo.setOffset( Optional.ofNullable( reader.getAttribute( dataset, OFFSET_KEY, double[].class ) ).orElse( new double[ nDim ] ) );
			this.datasetInfo.minProperty().set( Optional.ofNullable( reader.getAttribute( dataset, MIN_KEY, Double.class ) ).orElse( minForType( dsAttrs.getDataType() ) ) );
			this.datasetInfo.maxProperty().set( Optional.ofNullable( reader.getAttribute( dataset, MAX_KEY, Double.class ) ).orElse( maxForType( dsAttrs.getDataType() ) ) );

		}
		catch ( final IOException e )
		{

		}

	}

	@Override
	public List< String > discoverDatasetAt( final String at )
	{
		final ArrayList< File > files = new ArrayList<>();
		final URI baseURI = new File( at ).toURI();
		discoverSubdirectories( new File( at ), dir -> new File( dir, "attributes.json" ).exists(), files::add, () -> this.isTraversingDirectories.set( false ) );
		return files.stream().map( File::toURI ).map( baseURI::relativize ).map( URI::getPath ).collect( Collectors.toList() );
	}

	public static void discoverSubdirectories( final File file, final Predicate< File > check, final Consumer< File > action, final Runnable onInterruption )
	{

		if ( !Thread.currentThread().isInterrupted() )
		{
			if ( check.test( file ) )
				action.accept( file );
			else if ( file.exists() )
				// TODO come up with better filter than File::canWrite
				Optional.ofNullable( file.listFiles() ).ifPresent( files -> Arrays.stream( files ).filter( File::isDirectory ).filter( File::canRead ).forEach( f -> discoverSubdirectories( f, check, action, onInterruption ) ) );
		}
		else
			onInterruption.run();
	}

}
